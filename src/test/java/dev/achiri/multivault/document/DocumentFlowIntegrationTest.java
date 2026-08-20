package dev.achiri.multivault.document;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.achiri.multivault.apikey.model.ApiKey;
import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.apikey.repository.ApiKeyRepository;
import dev.achiri.multivault.apikey.service.ApiKeyHasher;
import dev.achiri.multivault.audit.repository.AuditLogRepository;
import dev.achiri.multivault.common.exception.StorageException;
import dev.achiri.multivault.document.service.DocumentHashUtil;
import dev.achiri.multivault.infrastructure.storage.DocumentStorageService;
import dev.achiri.multivault.plan.model.Plan;
import dev.achiri.multivault.plan.repository.PlanRepository;
import dev.achiri.multivault.support.BaseIntegrationTest;
import dev.achiri.multivault.tenant.dto.CreateTenantRequest;
import dev.achiri.multivault.tenant.dto.CreateTenantResponse;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import dev.achiri.multivault.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class DocumentFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiKeyHasher apiKeyHasher;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private DocumentStorageService documentStorageService;

    private final List<UUID> tenantIds = new java.util.ArrayList<>();
    private final List<String> schemaNames = new java.util.ArrayList<>();
    private UUID serviceKeyId;

    @AfterEach
    void tearDown() {
        schemaNames.forEach(schema -> jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"));
        tenantIds.forEach(tenantId -> {
            auditLogRepository.deleteAll(auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId));
            tenantRepository.deleteById(tenantId);
        });
    }

    @Test
    void createsDocumentWithVersionAndOwnerPermission() throws Exception {
        doNothing().when(documentStorageService).upload(anyString(), any(), anyString(), anyLong());

        CreateTenantResponse tenant = createTenant("Acme Docs", "sub_docs_a");
        String schema = tenant.tenant().schemaName();
        String serviceKey = createServiceKey();
        UUID ownerUserId = UUID.randomUUID();

        MockMultipartFile file = new MockMultipartFile(
                "file", "Contract.pdf", "application/pdf", "test content".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .header(AUTHORIZATION, "Bearer " + serviceKey)
                        .param("name", "Contract.pdf")
                        .param("mimeType", "application/pdf")
                        .param("ownerUserId", ownerUserId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Contract.pdf"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currentVersion.versionNumber").value(1))
                .andExpect(jsonPath("$.currentVersion.createdBy").value(ownerUserId.toString()))
                .andReturn();

        UUID documentId = UUID.fromString(body(result).get("id").asText());

        Integer documentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".document WHERE id = ? AND owner_user_id = ? AND status = 'ACTIVE'",
                Integer.class, documentId, ownerUserId);
        assertThat(documentCount).isEqualTo(1);

        UUID currentVersionId = jdbcTemplate.queryForObject(
                "SELECT current_version_id FROM " + schema + ".document WHERE id = ?",
                UUID.class, documentId);
        assertThat(currentVersionId).isNotNull();

        String storedChecksum = DocumentHashUtil.sha256Hex("test content".getBytes());

        Integer versionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".document_version "
                        + "WHERE document_id = ? AND version_number = 1 AND created_by = ? AND storage_key = ?",
                Integer.class, documentId, ownerUserId,
                schema + "/" + documentId + "/1/" + storedChecksum);
        assertThat(versionCount).isEqualTo(1);

        Integer ownerPermissions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".document_permission "
                        + "WHERE document_id = ? AND permission_level = 'OWNER' AND user_id = ?",
                Integer.class, documentId, ownerUserId);
        assertThat(ownerPermissions).isEqualTo(1);

        List<Map<String, Object>> auditRows = jdbcTemplate.queryForList(
                "SELECT action, actor_user_id, api_key_id, resource_id FROM public.audit_log "
                        + "WHERE tenant_id = ? AND action = 'DOCUMENT_CREATED'",
                tenant.tenant().id());
        assertThat(auditRows).hasSize(1);
        assertThat(auditRows.getFirst().get("actor_user_id")).isEqualTo(ownerUserId);
        assertThat(auditRows.getFirst().get("api_key_id")).isEqualTo(serviceKeyId);
        assertThat(auditRows.getFirst().get("resource_id")).isEqualTo(documentId);

        String documentName = jdbcTemplate.queryForObject(
                "SELECT metadata ->> 'document_name' FROM public.audit_log WHERE tenant_id = ? AND action = 'DOCUMENT_CREATED'",
                String.class, tenant.tenant().id());
        assertThat(documentName).isEqualTo("Contract.pdf");
    }

    @Test
    void addsImmutableVersionAndRepointsCurrent() throws Exception {
        doNothing().when(documentStorageService).upload(anyString(), any(), anyString(), anyLong());

        CreateTenantResponse tenant = createTenant("Acme Versions", "sub_docs_v");
        String schema = tenant.tenant().schemaName();
        String serviceKey = createServiceKey();
        UUID ownerUserId = UUID.randomUUID();

        MockMultipartFile file = new MockMultipartFile(
                "file", "Draft.pdf", "application/pdf", "draft v1".getBytes());

        MvcResult createResult = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .header(AUTHORIZATION, "Bearer " + serviceKey)
                        .param("name", "Draft.pdf")
                        .param("mimeType", "application/pdf")
                        .param("ownerUserId", ownerUserId.toString()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID documentId = UUID.fromString(body(createResult).get("id").asText());
        UUID firstVersionId = UUID.fromString(body(createResult).path("currentVersion").get("id").asText());

        MockMultipartFile fileV2 = new MockMultipartFile(
                "file", "Draft_v2.pdf", "application/pdf", "draft v2 content".getBytes());

        MvcResult versionResult = mockMvc.perform(multipart("/api/v1/documents/{documentId}/versions", documentId)
                        .file(fileV2)
                        .header(AUTHORIZATION, "Bearer " + serviceKey)
                        .param("name", "Draft_v2.pdf")
                        .param("mimeType", "application/pdf")
                        .param("ownerUserId", ownerUserId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.name").value("Draft_v2.pdf"))
                .andReturn();
        UUID versionId = UUID.fromString(body(versionResult).get("id").asText());

        Integer versionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".document_version WHERE document_id = ?",
                Integer.class, documentId);
        assertThat(versionCount).isEqualTo(2);

        Integer firstVersionIntact = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".document_version WHERE id = ? AND version_number = 1 AND size_bytes = 8",
                Integer.class, firstVersionId);
        assertThat(firstVersionIntact).isEqualTo(1);

        Integer repointed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".document d "
                        + "JOIN " + schema + ".document_version dv ON dv.id = d.current_version_id "
                        + "WHERE d.id = ? AND dv.version_number = 2",
                Integer.class, documentId);
        assertThat(repointed).isEqualTo(1);

        List<Map<String, Object>> auditRows = jdbcTemplate.queryForList(
                "SELECT action, actor_user_id, api_key_id, resource_id FROM public.audit_log "
                        + "WHERE tenant_id = ? AND action IN ('DOCUMENT_CREATED', 'DOCUMENT_VERSION_UPLOADED') "
                        + "ORDER BY created_at",
                tenant.tenant().id());
        assertThat(auditRows).extracting(row -> row.get("action"))
                .containsExactly("DOCUMENT_CREATED", "DOCUMENT_VERSION_UPLOADED");
        assertThat(auditRows.getFirst().get("actor_user_id")).isEqualTo(ownerUserId);
        assertThat(auditRows.getFirst().get("api_key_id")).isEqualTo(serviceKeyId);
        assertThat(auditRows.getLast().get("resource_id")).isEqualTo(versionId);

        String versionNumber = jdbcTemplate.queryForObject(
                "SELECT metadata ->> 'version_number' FROM public.audit_log "
                        + "WHERE tenant_id = ? AND action = 'DOCUMENT_VERSION_UPLOADED'",
                String.class, tenant.tenant().id());
        assertThat(versionNumber).isEqualTo("2");
    }

    @Test
    void getIsScopedToRequestTenant() throws Exception {
        doNothing().when(documentStorageService).upload(anyString(), any(), anyString(), anyLong());

        String schemaA = createTenant("Acme Scope A", "sub_scope_a").tenant().schemaName();
        String keyA = createServiceKey();
        createTenant("Acme Scope B", "sub_scope_b");
        String keyB = createServiceKey();
        UUID ownerUserId = UUID.randomUUID();

        MockMultipartFile file = new MockMultipartFile(
                "file", "Secret.pdf", "application/pdf", "secret content".getBytes());

        MvcResult createResult = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .header(AUTHORIZATION, "Bearer " + keyA)
                        .param("name", "Secret.pdf")
                        .param("mimeType", "application/pdf")
                        .param("ownerUserId", ownerUserId.toString()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID documentId = UUID.fromString(body(createResult).get("id").asText());

        mockMvc.perform(get("/api/v1/documents/{documentId}", documentId)
                        .header(AUTHORIZATION, "Bearer " + keyB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/documents/{documentId}", documentId)
                        .header(AUTHORIZATION, "Bearer " + keyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Secret.pdf"));

        Integer rowsInOtherSchema = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'document'",
                Integer.class, schemaA);
        assertThat(rowsInOtherSchema).isEqualTo(1);
    }

    @Test
    void requiresOwnerUserIdForServiceKey() throws Exception {
        createTenant("Acme No Owner", "sub_no_owner");
        String serviceKey = createServiceKey();

        MockMultipartFile file = new MockMultipartFile(
                "file", "Orphan.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .header(AUTHORIZATION, "Bearer " + serviceKey)
                        .param("name", "Orphan.pdf")
                        .param("mimeType", "application/pdf"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadFailureRollsBackTransaction() throws Exception {
        doThrow(new StorageException("B2 upload failed"))
                .when(documentStorageService).upload(anyString(), any(), anyString(), anyLong());

        createTenant("Acme Rollback", "sub_rollback");
        String serviceKey = createServiceKey();
        UUID ownerUserId = UUID.randomUUID();

        MockMultipartFile file = new MockMultipartFile(
                "file", "Fail.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .header(AUTHORIZATION, "Bearer " + serviceKey)
                        .param("name", "Fail.pdf")
                        .param("mimeType", "application/pdf")
                        .param("ownerUserId", ownerUserId.toString()))
                .andExpect(status().isInternalServerError());

        String schema = tenantIds.isEmpty() ? null :
                jdbcTemplate.queryForObject(
                        "SELECT schema_name FROM public.tenant WHERE id = ?",
                        String.class, tenantIds.getLast());

        Integer documentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".document",
                Integer.class);
        assertThat(documentCount).isEqualTo(0);

        Integer versionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".document_version",
                Integer.class);
        assertThat(versionCount).isEqualTo(0);
    }

    @Test
    void usesFileOriginalFilenameWhenNameOmitted() throws Exception {
        doNothing().when(documentStorageService).upload(anyString(), any(), anyString(), anyLong());

        createTenant("Acme Filename", "sub_filename");
        String serviceKey = createServiceKey();
        UUID ownerUserId = UUID.randomUUID();

        MockMultipartFile file = new MockMultipartFile(
                "file", "OriginalName.pdf", "application/pdf", "content".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .header(AUTHORIZATION, "Bearer " + serviceKey)
                        .param("mimeType", "application/pdf")
                        .param("ownerUserId", ownerUserId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("OriginalName.pdf"))
                .andReturn();
    }

    @Test
    void usesFileContentTypeWhenMimeTypeOmitted() throws Exception {
        doNothing().when(documentStorageService).upload(anyString(), any(), anyString(), anyLong());

        createTenant("Acme Mime", "sub_mime");
        String serviceKey = createServiceKey();
        UUID ownerUserId = UUID.randomUUID();

        MockMultipartFile file = new MockMultipartFile(
                "file", "Doc.txt", "text/plain", "content".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .header(AUTHORIZATION, "Bearer " + serviceKey)
                        .param("name", "Doc.txt")
                        .param("ownerUserId", ownerUserId.toString()))
                .andExpect(status().isCreated())
                .andReturn();

        UUID documentId = UUID.fromString(body(result).get("id").asText());
        String schema = tenantIds.isEmpty() ? null :
                jdbcTemplate.queryForObject(
                        "SELECT schema_name FROM public.tenant WHERE id = ?",
                        String.class, tenantIds.getLast());

        String storedMimeType = jdbcTemplate.queryForObject(
                "SELECT mime_type FROM " + schema + ".document_version WHERE document_id = ?",
                String.class, documentId);
        assertThat(storedMimeType).isEqualTo("text/plain");
    }

    @Test
    void checksumIsComputedServerSide() throws Exception {
        doNothing().when(documentStorageService).upload(anyString(), any(), anyString(), anyLong());

        createTenant("Acme Checksum", "sub_checksum");
        String serviceKey = createServiceKey();
        UUID ownerUserId = UUID.randomUUID();
        byte[] fileBytes = "checksum test payload".getBytes();
        String expectedChecksum = DocumentHashUtil.sha256Hex(fileBytes);

        MockMultipartFile file = new MockMultipartFile(
                "file", "Checksum.pdf", "application/pdf", fileBytes);

        MvcResult result = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .header(AUTHORIZATION, "Bearer " + serviceKey)
                        .param("name", "Checksum.pdf")
                        .param("mimeType", "application/pdf")
                        .param("ownerUserId", ownerUserId.toString()))
                .andExpect(status().isCreated())
                .andReturn();

        UUID documentId = UUID.fromString(body(result).get("id").asText());
        String schema = tenantIds.isEmpty() ? null :
                jdbcTemplate.queryForObject(
                        "SELECT schema_name FROM public.tenant WHERE id = ?",
                        String.class, tenantIds.getLast());

        String storedChecksum = jdbcTemplate.queryForObject(
                "SELECT checksum FROM " + schema + ".document_version WHERE document_id = ?",
                String.class, documentId);
        assertThat(storedChecksum).isEqualTo(expectedChecksum);

        Long storedSize = jdbcTemplate.queryForObject(
                "SELECT size_bytes FROM " + schema + ".document_version WHERE document_id = ?",
                Long.class, documentId);
        assertThat(storedSize).isEqualTo((long) fileBytes.length);
    }

    @Test
    void storageKeyMatchesFormat() throws Exception {
        doNothing().when(documentStorageService).upload(anyString(), any(), anyString(), anyLong());

        CreateTenantResponse tenant = createTenant("Acme Key", "sub_key");
        String schema = tenant.tenant().schemaName();
        String serviceKey = createServiceKey();
        UUID ownerUserId = UUID.randomUUID();
        byte[] fileBytes = "key format test".getBytes();
        String checksum = DocumentHashUtil.sha256Hex(fileBytes);

        MockMultipartFile file = new MockMultipartFile(
                "file", "Key.pdf", "application/pdf", fileBytes);

        MvcResult result = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .header(AUTHORIZATION, "Bearer " + serviceKey)
                        .param("name", "Key.pdf")
                        .param("mimeType", "application/pdf")
                        .param("ownerUserId", ownerUserId.toString()))
                .andExpect(status().isCreated())
                .andReturn();

        UUID documentId = UUID.fromString(body(result).get("id").asText());

        String storageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM " + schema + ".document_version WHERE document_id = ?",
                String.class, documentId);
        assertThat(storageKey).isEqualTo(schema + "/" + documentId + "/1/" + checksum);
    }

    @Test
    void addVersionRejectsDeletedDocument() throws Exception {
        doNothing().when(documentStorageService).upload(anyString(), any(), anyString(), anyLong());

        CreateTenantResponse tenant = createTenant("Acme Deleted", "sub_deleted");
        String schema = tenant.tenant().schemaName();
        String serviceKey = createServiceKey();
        UUID ownerUserId = UUID.randomUUID();

        MockMultipartFile file = new MockMultipartFile(
                "file", "DeleteMe.pdf", "application/pdf", "content".getBytes());

        MvcResult createResult = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .header(AUTHORIZATION, "Bearer " + serviceKey)
                        .param("name", "DeleteMe.pdf")
                        .param("mimeType", "application/pdf")
                        .param("ownerUserId", ownerUserId.toString()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID documentId = UUID.fromString(body(createResult).get("id").asText());

        jdbcTemplate.execute("UPDATE " + schema + ".document SET deleted_at = now() WHERE id = '" + documentId + "'");

        MockMultipartFile fileV2 = new MockMultipartFile(
                "file", "DeleteMe_v2.pdf", "application/pdf", "content v2".getBytes());

        mockMvc.perform(multipart("/api/v1/documents/{documentId}/versions", documentId)
                        .file(fileV2)
                        .header(AUTHORIZATION, "Bearer " + serviceKey)
                        .param("name", "DeleteMe_v2.pdf")
                        .param("mimeType", "application/pdf")
                        .param("ownerUserId", ownerUserId.toString()))
                .andExpect(status().isNotFound());
    }

    private CreateTenantResponse createTenant(String name, String subject) {
        Plan plan = planRepository.findAll().stream().filter(Plan::getIsActive).findFirst().orElseThrow();
        CreateTenantResponse response = tenantService.create(new CreateTenantRequest(
                name,
                plan.getId(),
                new CreateTenantRequest.TenantAdminDto(subject, subject + "@acme.com", "Admin"),
                null));
        tenantIds.add(response.tenant().id());
        schemaNames.add(response.tenant().schemaName());
        return response;
    }

    private String createServiceKey() {
        UUID tenantId = tenantIds.get(tenantIds.size() - 1);
        String rawKey = randomRawKey();
        ApiKey key = new ApiKey();
        key.setTenantId(tenantId);
        key.setName("Docs Service Key");
        key.setKeyPrefix(rawKey.substring(0, 12));
        key.setKeyHash(apiKeyHasher.sha256Hex(rawKey));
        key.setKeyType(ApiKeyType.SERVICE);
        key.setScopes(List.of("documents:write", "documents:read"));
        key.setCreatedByUserId(UUID.randomUUID());
        apiKeyRepository.saveAndFlush(key);
        serviceKeyId = key.getId();
        return rawKey;
    }

    private String randomRawKey() {
        String hexA = UUID.randomUUID().toString().replace("-", "");
        String hexB = UUID.randomUUID().toString().replace("-", "");
        return "mv_live_" + hexA + hexB.substring(0, 8);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
