package dev.achiri.multivault.tenant;

import dev.achiri.multivault.apikey.repository.ApiKeyRepository;
import dev.achiri.multivault.audit.model.AuditLog;
import dev.achiri.multivault.audit.repository.AuditLogRepository;
import dev.achiri.multivault.common.exception.RecursoDuplicadoException;
import dev.achiri.multivault.common.exception.RecursoNoEncontradoException;
import dev.achiri.multivault.infrastructure.async.ProvisioningWorker;
import dev.achiri.multivault.infrastructure.async.ProvisioningProperties;
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
import dev.achiri.multivault.tenant.provisioning.TenantProvisioningService;
import dev.achiri.multivault.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TenantProvisioningTest extends BaseIntegrationTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private ProvisioningProperties provisioningProperties;

    @Autowired
    private TenantProvisioningService tenantProvisioningService;

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

    @Autowired
    private StringRedisTemplate redis;

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
    void returnsTenantPendingProvisioningWithoutCreatingTheSchema() {
        Plan plan = activePlan();

        CreateTenantResponse response = tenantService.create(
                request("Acme Pending", plan.getId(), "sub_1", "admin@acme.com", identityProvider()));
        track(response);

        assertThat(response.tenant().status()).isEqualTo(TenantStatus.PENDING_PROVISIONING);
        assertThat(response.apiKey().key()).startsWith("mv_live_");
        assertThat(response.apiKey().keyPrefix()).hasSize(12);
        assertThat(response.apiKey().keyType()).isEqualTo("SERVICE");
        assertThat(schemaExists(schemaName)).isFalse();
    }

    @Test
    void workerProvisionsTheSchemaAndActivatesTheTenant() {
        Plan plan = activePlan();
        CreateTenantResponse response = tenantService.create(
                request("Acme Provisioned", plan.getId(), "sub_1", "admin@acme.com", identityProvider()));
        track(response);

        awaitProvisioning();

        Tenant stored = tenantRepository.findById(tenantId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(stored.getCurrentPlanId()).isEqualTo(plan.getId());
        assertThat(schemaExists(schemaName)).isTrue();

        List<String> tenantTables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ?",
                String.class, schemaName);
        assertThat(tenantTables).contains(
                "folder", "document", "document_version", "document_permission", "flyway_schema_history");

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
    void redeliveredJobDoesNotDuplicateTheAuditEntry() {
        Plan plan = activePlan();
        CreateTenantResponse response = tenantService.create(
                request("Acme Redelivered", plan.getId(), "sub_20", "admin20@acme.com", identityProvider()));
        track(response);

        awaitProvisioning();
        assertThat(tenantProvisioningService.activateIfPending(tenantId, plan.getId())).isFalse();

        List<AuditLog> logs = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        assertThat(logs.stream().filter(log -> log.getAction().equals("TENANT_CREATED")).count()).isEqualTo(1);
    }

    @Test
    void appliesDocumentOwnerPermissionTriggerOnInsert() {
        Plan plan = activePlan();
        CreateTenantResponse response = tenantService.create(
                request("Acme Trigger", plan.getId(), "sub_9", "admin9@acme.com", identityProvider()));
        track(response);
        awaitProvisioning();

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
        track(response);
        awaitProvisioning();

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
        track(tenantService.create(first));

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
        Plan plan = activePlan();

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
    void suspendsTenantWhenSchemaProvisioningKeepsFailing() {
        Plan plan = activePlan();
        doThrow(new RuntimeException("boom")).when(tenantSchemaProvisioner).provision(anyString());

        CreateTenantResponse response = tenantService.create(
                request("Acme Broken Schema", plan.getId(), "sub_8", "admin8@acme.com", identityProvider()));
        track(response);

        awaitStatus(TenantStatus.SUSPENDED);

        Tenant stored = tenantRepository.findById(tenantId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(stored.getSuspendedReason()).isEqualTo("schema_provisioning_failed");
    }

    @Test
    void exhaustedProvisioningSendsTheJobToTheDeadLetterStream() {
        Plan plan = activePlan();
        doThrow(new RuntimeException("boom")).when(tenantSchemaProvisioner).provision(anyString());

        CreateTenantResponse response = tenantService.create(
                request("Acme DeadLetter", plan.getId(), "sub_21", "admin21@acme.com", identityProvider()));
        track(response);

        awaitStatus(TenantStatus.SUSPENDED);

        assertThat(deadLetteredJobs()).isEqualTo(1);
    }

    private long deadLetteredJobs() {
        var records = redis.opsForStream().range(provisioningProperties.deadLetterKey(),
                org.springframework.data.domain.Range.unbounded());
        return records == null ? 0 : records.stream()
                .filter(record -> tenantId.toString().equals(String.valueOf(record.getValue().get("tenantId"))))
                .count();
    }

    @Test
    @WithMockUser
    void rejectsInvalidRequestBody() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
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
    void returnsAcceptedWhileProvisioning() throws Exception {
        Plan plan = activePlan();

        var result = mockMvc.perform(post("/api/v1/tenants")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Acme Accepted",
                                  "planId": "%s",
                                  "admin": {
                                    "subject": "sub_accepted",
                                    "email": "accepted@acme.com"
                                  },
                                  "identityProvider": {
                                    "issuer": "https://idp.acme.com",
                                    "jwksUri": "https://idp.acme.com/v2/.well-known/jwks.json",
                                    "audience": "https://api.acme.com",
                                    "allowedAlgorithms": ["RS256"]
                                  }
                                }
                                """.formatted(plan.getId())))
                .andExpect(status().isAccepted())
                .andReturn();
        tenantId = UUID.fromString(tenantIdOf(result.getResponse().getContentAsString()));
        schemaName = "mv_acme_accepted";
    }

    @Test
    void updatesTenantIdentityProvider() throws Exception {
        Plan plan = activePlan();
        CreateTenantResponse response = tenantService.create(
                request("Acme Update IdP", plan.getId(), "sub_11", "admin11@acme.com", identityProvider()));
        track(response);
        awaitProvisioning();

        mockMvc.perform(put("/api/v1/tenants/identity-provider")
                        .header("Authorization", "Bearer " + response.apiKey().key())
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
    void rejectsInvalidIdentityProviderBody() throws Exception {
        Plan plan = activePlan();
        CreateTenantResponse response = tenantService.create(
                request("Acme Invalid IdP", plan.getId(), "sub_12", "admin12@acme.com", identityProvider()));
        track(response);
        awaitProvisioning();

        mockMvc.perform(put("/api/v1/tenants/identity-provider")
                        .header("Authorization", "Bearer " + response.apiKey().key())
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

    private void awaitProvisioning() {
        awaitStatus(TenantStatus.ACTIVE);
    }

    private void awaitStatus(TenantStatus expected) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            Tenant stored = tenantRepository.findById(tenantId).orElseThrow();
            if (stored.getStatus() == expected) {
                return;
            }
            sleepBriefly();
        }
        assertThat(tenantRepository.findById(tenantId).orElseThrow().getStatus()).isEqualTo(expected);
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean schemaExists(String schema) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class, schema);
        return count != null && count > 0;
    }

    private void track(CreateTenantResponse response) {
        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();
    }

    private String tenantIdOf(String body) {
        int start = body.indexOf("\"tenant\":{\"id\":\"") + "\"tenant\":{\"id\":\"".length();
        return body.substring(start, body.indexOf('"', start));
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
                "https://idp.acme.com/v2/.well-known/jwks.json",
                "https://api.acme.com",
                null,
                null);
    }
}