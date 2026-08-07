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
import dev.achiri.multivault.tenant.dto.CreateTenantRequest;
import dev.achiri.multivault.tenant.dto.CreateTenantResponse;
import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.model.TenantStatus;
import dev.achiri.multivault.tenant.repository.TenantIdentityProviderRepository;
import dev.achiri.multivault.tenant.repository.TenantMemberRepository;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import dev.achiri.multivault.tenant.repository.TenantUsageRepository;
import dev.achiri.multivault.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TenantProvisioningTest {

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

        assertThat(tenantUsageRepository.findById(tenantId)).isPresent();
        assertThat(tenantMemberRepository.findAll()).anyMatch(member -> member.getTenantId().equals(tenantId));
        assertThat(apiKeyRepository.findAll()).anyMatch(key -> key.getTenantId().equals(tenantId));

        List<AuditLog> logs = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        assertThat(logs).anyMatch(log -> log.getAction().equals("TENANT_CREATED"));
    }

    @Test
    void createsTenantWithoutIdentityProvider() {
        Plan plan = activePlan();

        CreateTenantResponse response = tenantService.create(
                request("Acme No Identity Provider", plan.getId(), "sub_2", "admin2@acme.com", null));

        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();

        assertThat(response.identityProvider()).isNull();
        assertThat(response.tenant().status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenantIdentityProviderRepository.findById(tenantId)).isEmpty();
    }

    @Test
    void rejectsDuplicateSchemaName() {
        Plan plan = activePlan();
        CreateTenantRequest first = request("Acme Duplicate", plan.getId(), "sub_3", "admin3@acme.com", null);
        CreateTenantRequest second = request("Acme Duplicate", plan.getId(), "sub_4", "admin4@acme.com", null);

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
                request("!!!", plan.getId(), "sub_5", "admin5@acme.com", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonexistentPlan() {
        assertThatThrownBy(() -> tenantService.create(
                request("Acme No Plan", UUID.randomUUID(), "sub_6", "admin6@acme.com", null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void rejectsInactivePlan() {
        Plan plan = activePlan();
        plan.setIsActive(false);
        planRepository.save(plan);
        try {
            assertThatThrownBy(() -> tenantService.create(
                    request("Acme Inactive Plan", plan.getId(), "sub_7", "admin7@acme.com", null)))
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
                request("Acme Broken Schema", plan.getId(), "sub_8", "admin8@acme.com", null)))
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
