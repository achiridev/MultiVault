package dev.achiri.multivault.tenant;

import dev.achiri.multivault.infrastructure.async.ProvisioningProperties;
import dev.achiri.multivault.infrastructure.persistence.tenant.TenantSchemaProvisioner;
import dev.achiri.multivault.plan.model.Plan;
import dev.achiri.multivault.plan.repository.PlanRepository;
import dev.achiri.multivault.support.BaseIntegrationTest;
import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.model.TenantStatus;
import dev.achiri.multivault.tenant.provisioning.ProvisioningReconciler;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

class ProvisioningReconcilerTest extends BaseIntegrationTest {

    @Autowired
    private ProvisioningReconciler reconciler;

    @Autowired
    private ProvisioningProperties properties;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redis;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private TenantSchemaProvisioner tenantSchemaProvisioner;

    private UUID tenantId;
    private String schemaName;

    @BeforeEach
    void breakSchemaProvisioning() {
        doThrow(new IllegalStateException("no se puede aprovisionar en este test"))
                .when(tenantSchemaProvisioner).provision(anyString());
    }

    @AfterEach
    void tearDown() {
        if (schemaName != null) {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
        if (tenantId != null) {
            tenantRepository.deleteById(tenantId);
        }
    }

    @Test
    void reenqueuesTenantsStuckInPendingProvisioning() {
        long before = enqueuedJobs();
        UUID tenant = stuckTenantWithPlan();

        reconciler.reconcile();

        assertThat(enqueuedJobs()).isEqualTo(before + 1);
        assertThat(tenantRepository.findById(tenant).orElseThrow().getStatus())
                .isEqualTo(TenantStatus.PENDING_PROVISIONING);
    }

    @Test
    void leavesActiveTenantsAlone() {
        long before = enqueuedJobs();
        UUID tenant = stuckTenantWithPlan();
        jdbcTemplate.update("UPDATE tenant SET status = 'ACTIVE' WHERE id = ?", tenant);

        reconciler.reconcile();

        assertThat(enqueuedJobs()).isEqualTo(before);
    }

    @Test
    void reenqueuedJobReachesTheSameTerminalFailure() {
        UUID tenant = stuckTenantWithPlan();

        reconciler.reconcile();
        awaitActiveTenant(tenant);

        assertThat(tenantRepository.findById(tenant).orElseThrow().getStatus())
                .isEqualTo(TenantStatus.SUSPENDED);
    }

    private long enqueuedJobs() {
        Long size = redis.opsForStream().size(properties.streamKey());
        return size == null ? 0 : size;
    }

    @Test
    void doesNotEnqueueTenantsWithoutAPlan() {
        Tenant tenant = new Tenant();
        tenant.setName("Reconciler No Plan " + UUID.randomUUID());
        tenant.setSchemaName("mv_rec_no_plan_" + shortId());
        tenant.setStatus(TenantStatus.PENDING_PROVISIONING);
        tenantRepository.save(tenant);
        tenantId = tenant.getId();
        schemaName = tenant.getSchemaName();
        jdbcTemplate.update("UPDATE tenant SET updated_at = now() - interval '1 hour' WHERE id = ?", tenantId);

        long before = enqueuedJobs();

        reconciler.reconcile();

        assertThat(enqueuedJobs()).isEqualTo(before);
    }

    private UUID stuckTenantWithPlan() {
        Tenant tenant = new Tenant();
        tenant.setName("Reconciler " + UUID.randomUUID());
        tenant.setSchemaName("mv_reconciler_" + shortId());
        tenant.setStatus(TenantStatus.PENDING_PROVISIONING);
        tenant.setCurrentPlanId(activePlan().getId());
        tenantRepository.save(tenant);
        tenantId = tenant.getId();
        schemaName = tenant.getSchemaName();
        jdbcTemplate.update("UPDATE tenant SET updated_at = now() - interval '1 hour' WHERE id = ?", tenantId);
        return tenantId;
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private Plan activePlan() {
        return planRepository.findAll().stream().filter(Plan::getIsActive).findFirst().orElseThrow();
    }
}