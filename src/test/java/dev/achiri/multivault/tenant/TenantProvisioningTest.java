package dev.achiri.multivault.tenant;

import dev.achiri.multivault.apikey.repository.ApiKeyRepository;
import dev.achiri.multivault.audit.model.AuditLog;
import dev.achiri.multivault.audit.repository.AuditLogRepository;
import dev.achiri.multivault.common.exception.RecursoDuplicadoException;
import dev.achiri.multivault.common.exception.RecursoNoEncontradoException;
import dev.achiri.multivault.common.exception.TenantProvisioningException;
import dev.achiri.multivault.infrastructure.persistence.tenant.TenantSchemaProvisioner;
import dev.achiri.multivault.plan.model.Plan;
import dev.achiri.multivault.plan.repository.PlanRepository;
import dev.achiri.multivault.support.BaseIntegrationTest;
import dev.achiri.multivault.tenant.dto.CreateTenantRequest;
import dev.achiri.multivault.tenant.dto.CreateTenantResponse;
import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.model.TenantIdentityProvider;
import dev.achiri.multivault.tenant.model.TenantStatus;
import dev.achiri.multivault.tenant.repository.TenantIdentityProviderRepository;
import dev.achiri.multivault.tenant.repository.TenantMemberRepository;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import dev.achiri.multivault.tenant.repository.TenantUsageRepository;
import dev.achiri.multivault.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TenantProvisioningTest extends BaseIntegrationTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantUsageRepository tenantUsageRepository;

    @Autowired
    private TenantMemberRepository tenantMemberRepository;

    @Autowired
    private TenantIdentityProviderRepository tenantIdentityProviderRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private TenantSchemaProvisioner tenantSchemaProvisioner;

    private UUID tenantId;
    private String schemaName;

    @AfterEach
    void tearDown() {
        if (schemaName != null) {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
        if (tenantId != null) {
            auditLogRepository.deleteAll(auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId));
            tenantRepository.deleteById(tenantId);
        }
    }

    @Test
    void createsTenantWithSchemaUsageApiKeyAndAudit() {
        Plan plan = activePlan();

        CreateTenantResponse response = tenantService.create(
                request("Acme Provisioned", plan.getId(), "sub_1", "admin@acme.com", identityProvider()));

        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();

        assertThat(response.tenant().status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(response.apiKey().key()).startsWith("mv_live_");
        assertThat(response.apiKey().keyPrefix()).hasSize(12);
        assertThat(response.apiKey().keyType()).isEqualTo("STANDARD");

        var stored = tenantRepository.findById(tenantId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(stored.getCurrentPlanId()).isEqualTo(plan.getId());

        Integer schemaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class, schemaName);
        assertThat(schemaCount).isEqualTo(1);

        Integer folderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schemaName + ".folder", Integer.class);
        assertThat(folderCount).isZero();

        List<String> tenantTables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ?",
                String.class, schemaName);
        assertThat(tenantTables).contains(
                "folder", "document", "document_version", "document_permission", "flyway_schema_history");

        Integer triggerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema = ? AND trigger_name = ?",
                Integer.class, schemaName, "trg_document_owner_permission");
        assertThat(triggerCount).isEqualTo(1);

        Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = ? AND indexname IN (?, ?, ?, ?)",
                Integer.class, schemaName, "uq_folder_root_name", "uq_folder_parent_name_active",
                "uq_document_permission", "uq_document_single_owner");
        assertThat(indexCount).isEqualTo(4);

        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schemaName + ".flyway_schema_history WHERE success = true",
                Integer.class);
        assertThat(appliedMigrations).isEqualTo(2);

        assertThat(tenantUsageRepository.findById(tenantId)).isPresent();
        assertThat(tenantMemberRepository.findAll()).anyMatch(member -> member.getTenantId().equals(tenantId));
        assertThat(apiKeyRepository.findAll()).anyMatch(key -> key.getTenantId().equals(tenantId));

        List<AuditLog> logs = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        assertThat(logs).anyMatch(log -> log.getAction().equals("TENANT_CREATED"));
    }

    @Test
    void appliesDocumentOwnerPermissionTriggerOnInsert() {
        Plan plan = activePlan();

        CreateTenantResponse response = tenantService.create(
                request("Acme Trigger", plan.getId(), "sub_9", "admin9@acme.com", identityProvider()));
        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();

        UUID folderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO " + schemaName + ".folder (id, name, created_by) VALUES (?, ?, ?)",
                folderId, "Invoices", ownerId);
        jdbcTemplate.update("INSERT INTO " + schemaName + ".document (id, folder_id, owner_user_id, status) "
                        + "VALUES (?, ?, ?, 'ACTIVE')",
                documentId, folderId, ownerId);

        Integer ownerPermissions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schemaName + ".document_permission "
                        + "WHERE document_id = ? AND permission_level = 'OWNER' AND user_id = ?",
                Integer.class, documentId, ownerId);
        assertThat(ownerPermissions).isEqualTo(1);
    }

    @Test
    void provisionIsIdempotent() {
        Plan plan = activePlan();

        CreateTenantResponse response = tenantService.create(
                request("Acme Idempotent", plan.getId(), "sub_10", "admin10@acme.com", identityProvider()));
        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();

        tenantSchemaProvisioner.provision(schemaName);

        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schemaName + ".flyway_schema_history WHERE success = true",
                Integer.class);
        assertThat(appliedMigrations).isEqualTo(2);
    }

    @Test
    void rejectsInvalidSchemaNames() {
        assertThatThrownBy(() -> tenantSchemaProvisioner.provision(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tenantSchemaProvisioner.provision(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tenantSchemaProvisioner.provision("1acme"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tenantSchemaProvisioner.provision("Acme"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tenantSchemaProvisioner.provision("ac me"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tenantSchemaProvisioner.provision("acme$"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser
    void rejectsTenantWithoutIdentityProvider() throws Exception {
        Plan plan = activePlan();

        mockMvc.perform(post("/api/v1/tenants")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Acme No Identity Provider",
                                  "planId": "%s",
                                  "admin": {
                                    "subject": "sub_2",
                                    "email": "admin2@acme.com"
                                  }
                                }
                                """.formatted(plan.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateSchemaName() {
        Plan plan = activePlan();
        CreateTenantRequest first = request("Acme Duplicate", plan.getId(), "sub_3", "admin3@acme.com", identityProvider());
        CreateTenantRequest second = request("Acme Duplicate", plan.getId(), "sub_4", "admin4@acme.com", identityProvider());

        CreateTenantResponse response = tenantService.create(first);
        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();

        assertThatThrownBy(() -> tenantService.create(second))
                .isInstanceOf(RecursoDuplicadoException.class);
    }

    @Test
    void rejectsNameWithoutValidSlug() {
        Plan plan = activePlan();

        assertThatThrownBy(() -> tenantService.create(
                request("!!!", plan.getId(), "sub_5", "admin5@acme.com", identityProvider())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonexistentPlan() {
        assertThatThrownBy(() -> tenantService.create(
                request("Acme No Plan", UUID.randomUUID(), "sub_6", "admin6@acme.com", identityProvider())))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void rejectsInactivePlan() {
        Plan plan = activePlan();
        plan.setIsActive(false);
        planRepository.save(plan);
        try {
            assertThatThrownBy(() -> tenantService.create(
                    request("Acme Inactive Plan", plan.getId(), "sub_7", "admin7@acme.com", identityProvider())))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        } finally {
            plan.setIsActive(true);
            planRepository.save(plan);
        }
    }

    @Test
    void suspendsTenantWhenSchemaProvisioningFails() {
        Plan plan = activePlan();
        doThrow(new RuntimeException("boom")).when(tenantSchemaProvisioner).provision(anyString());

        assertThatThrownBy(() -> tenantService.create(
                request("Acme Broken Schema", plan.getId(), "sub_8", "admin8@acme.com", identityProvider())))
                .isInstanceOf(TenantProvisioningException.class);

        Tenant stored = tenantRepository.findAll().stream()
                .filter(t -> t.getSchemaName().equals("mv_acme_broken_schema"))
                .findFirst()
                .orElseThrow();

        tenantId = stored.getId();
        schemaName = stored.getSchemaName();

        assertThat(stored.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(stored.getSuspendedReason()).isEqualTo("schema_provisioning_failed");
    }

    @Test
    @WithMockUser
    void rejectsInvalidRequestBody() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "planId": "00000000-0000-0000-0000-000000000000",
                                  "admin": {
                                    "subject": "",
                                    "email": "not-an-email"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_tenant:settings:write")
    void updatesTenantIdentityProvider() throws Exception {
        Plan plan = activePlan();

        CreateTenantResponse response = tenantService.create(
                request("Acme Update IdP", plan.getId(), "sub_11", "admin11@acme.com", identityProvider()));
        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();

        mockMvc.perform(put("/api/v1/tenants/{tenantId}/identity-provider", tenantId)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "issuer": "https://idp.acme.com/v2",
                                  "jwksUri": "https://idp.acme.com/v2/.well-known/jwks.json",
                                  "audience": "https://api.acme.com",
                                  "allowedAlgorithms": ["RS256"],
                                  "clockSkewSeconds": 120
                                }
                                """))
                .andExpect(status().isOk());

        TenantIdentityProvider stored = tenantIdentityProviderRepository.findById(tenantId).orElseThrow();
        assertThat(stored.getIssuer()).isEqualTo("https://idp.acme.com/v2");
        assertThat(stored.getJwksUri()).isEqualTo("https://idp.acme.com/v2/.well-known/jwks.json");
        assertThat(stored.getClockSkewSeconds()).isEqualTo(120);
    }

    @Test
    @WithMockUser(authorities = "SCOPE_tenant:settings:write")
    void rejectsIdentityProviderForUnknownTenant() throws Exception {
        mockMvc.perform(put("/api/v1/tenants/{tenantId}/identity-provider", UUID.randomUUID())
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "issuer": "https://idp.acme.com",
                                  "jwksUri": "https://idp.acme.com/.well-known/jwks.json",
                                  "audience": "https://api.acme.com"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_tenant:settings:write")
    void rejectsInvalidIdentityProviderBody() throws Exception {
        Plan plan = activePlan();

        CreateTenantResponse response = tenantService.create(
                request("Acme Invalid IdP", plan.getId(), "sub_12", "admin12@acme.com", identityProvider()));
        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();

        mockMvc.perform(put("/api/v1/tenants/{tenantId}/identity-provider", tenantId)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "issuer": "",
                                  "jwksUri": "https://idp.acme.com/.well-known/jwks.json",
                                  "audience": "https://api.acme.com"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private Plan activePlan() {
        return planRepository.findAll().stream().filter(Plan::getIsActive).findFirst().orElseThrow();
    }

    private CreateTenantRequest request(String name, UUID planId, String subject, String email,
                                        CreateTenantRequest.TenantIdentityProviderDto identityProvider) {
        return new CreateTenantRequest(name, planId,
                new CreateTenantRequest.TenantAdminDto(subject, email, "Admin"), identityProvider);
    }

    private CreateTenantRequest.TenantIdentityProviderDto identityProvider() {
        return new CreateTenantRequest.TenantIdentityProviderDto(
                "https://idp.acme.com",
                "https://idp.acme.com/.well-known/jwks.json",
                "https://api.acme.com",
                null,
                null);
    }
}
