package dev.achiri.multivault.document;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.achiri.multivault.apikey.model.ApiKey;
import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.apikey.repository.ApiKeyRepository;
import dev.achiri.multivault.apikey.service.ApiKeyHasher;
import dev.achiri.multivault.audit.repository.AuditLogRepository;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private final List<UUID> tenantIds = new java.util.ArrayList<>();
    private final List<String> schemaNames = new java.util.ArrayList<>();

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
        String schema = createTenant("Acme Docs", "sub_docs_a").tenant().schemaName();
        String serviceKey = createServiceKey();
        UUID ownerUserId = UUID.randomUUID();

        MvcResult result = mockMvc.perform(post("/api/v1/documents")
                        .header(AUTHORIZATION, "Bearer " + serviceKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Contract.pdf",
                                  "mimeType": "application/pdf",
                                  "sizeBytes": 2048,
                                  "checksum": "%s",
                                  "ownerUserId": "%s"
                                }
                                """.formatted(checksum(), ownerUserId)))
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

        Integer versionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".document_version "
                        + "WHERE document_id = ? AND version_number = 1 AND created_by = ? AND storage_key = ?",
                Integer.class, documentId, ownerUserId,
                schema + "/" + documentId + "/1/" + checksum());
        assertThat(versionCount).isEqualTo(1);

        Integer ownerPermissions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".document_permission "
                        + "WHERE document_id = ? AND permission_level = 'OWNER' AND user_id = ?",
                Integer.class, documentId, ownerUserId);
        assertThat(ownerPermissions).isEqualTo(1);
    }

    @Test
    void addsImmutableVersionAndRepointsCurrent() throws Exception {
        String schema = createTenant("Acme Versions", "sub_docs_v").tenant().schemaName();
        String serviceKey = createServiceKey();
        UUID ownerUserId = UUID.randomUUID();

        MvcResult createResult = mockMvc.perform(post("/api/v1/documents")
                        .header(AUTHORIZATION, "Bearer " + serviceKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Draft.pdf",
                                  "mimeType": "application/pdf",
                                  "sizeBytes": 1024,
                                  "checksum": "%s",
                                  "ownerUserId": "%s"
                                }
                                """.formatted(checksum(), ownerUserId)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID documentId = UUID.fromString(body(createResult).get("id").asText());
        UUID firstVersionId = UUID.fromString(body(createResult).path("currentVersion").get("id").asText());

        mockMvc.perform(post("/api/v1/documents/{documentId}/versions", documentId)
                        .header(AUTHORIZATION, "Bearer " + serviceKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Draft_v2.pdf",
                                  "mimeType": "application/pdf",
                                  "sizeBytes": 4096,
                                  "checksum": "%s",
                                  "ownerUserId": "%s"
                                }
                                """.formatted(checksum(), ownerUserId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.name").value("Draft_v2.pdf"));

        Integer versionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".document_version WHERE document_id = ?",
                Integer.class, documentId);
        assertThat(versionCount).isEqualTo(2);

        Integer firstVersionIntact = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".document_version WHERE id = ? AND version_number = 1 AND size_bytes = 1024",
                Integer.class, firstVersionId);
        assertThat(firstVersionIntact).isEqualTo(1);

        Integer repointed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".document d "
                        + "JOIN " + schema + ".document_version dv ON dv.id = d.current_version_id "
                        + "WHERE d.id = ? AND dv.version_number = 2",
                Integer.class, documentId);
        assertThat(repointed).isEqualTo(1);
    }

    @Test
    void getIsScopedToRequestTenant() throws Exception {
        String schemaA = createTenant("Acme Scope A", "sub_scope_a").tenant().schemaName();
        String keyA = createServiceKey();
        createTenant("Acme Scope B", "sub_scope_b");
        String keyB = createServiceKey();
        UUID ownerUserId = UUID.randomUUID();

        MvcResult createResult = mockMvc.perform(post("/api/v1/documents")
                        .header(AUTHORIZATION, "Bearer " + keyA)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Secret.pdf",
                                  "mimeType": "application/pdf",
                                  "sizeBytes": 128,
                                  "checksum": "%s",
                                  "ownerUserId": "%s"
                                }
                                """.formatted(checksum(), ownerUserId)))
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

        mockMvc.perform(post("/api/v1/documents")
                        .header(AUTHORIZATION, "Bearer " + serviceKey)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Orphan.pdf",
                                  "mimeType": "application/pdf",
                                  "sizeBytes": 512,
                                  "checksum": "%s"
                                }
                                """.formatted(checksum())))
                .andExpect(status().isBadRequest());
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
        return rawKey;
    }

    private String randomRawKey() {
        String hexA = UUID.randomUUID().toString().replace("-", "");
        String hexB = UUID.randomUUID().toString().replace("-", "");
        return "mv_live_" + hexA + hexB.substring(0, 8);
    }

    private String checksum() {
        return "a".repeat(64);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
