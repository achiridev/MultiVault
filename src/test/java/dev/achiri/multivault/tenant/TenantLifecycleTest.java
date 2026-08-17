package dev.achiri.multivault.tenant;

import dev.achiri.multivault.apikey.model.ApiKey;
import dev.achiri.multivault.apikey.repository.ApiKeyRepository;
import dev.achiri.multivault.audit.model.AuditLog;
import dev.achiri.multivault.audit.repository.AuditLogRepository;
import dev.achiri.multivault.common.exception.EstadoTransicionInvalidoException;
import dev.achiri.multivault.common.exception.RecursoNoEncontradoException;
import dev.achiri.multivault.plan.model.Plan;
import dev.achiri.multivault.plan.repository.PlanRepository;
import dev.achiri.multivault.subscription.model.Subscription;
import dev.achiri.multivault.subscription.model.SubscriptionStatus;
import dev.achiri.multivault.subscription.repository.SubscriptionRepository;
import dev.achiri.multivault.support.BaseIntegrationTest;
import dev.achiri.multivault.tenant.dto.CreateTenantRequest;
import dev.achiri.multivault.tenant.dto.CreateTenantResponse;
import dev.achiri.multivault.tenant.dto.TenantStatusResponse;
import dev.achiri.multivault.tenant.dto.UpdateTenantStatusRequest;
import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.model.TenantMember;
import dev.achiri.multivault.tenant.model.TenantStatus;
import dev.achiri.multivault.tenant.repository.TenantMemberRepository;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import dev.achiri.multivault.tenant.service.TenantLifecycleService;
import dev.achiri.multivault.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantLifecycleTest extends BaseIntegrationTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantLifecycleService tenantLifecycleService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private TenantMemberRepository tenantMemberRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void cancelsActiveTenant() {
        Plan plan = activePlan();
        CreateTenantResponse response = tenantService.create(
                request("Acme Cancel", plan.getId(), "sub_cancel_1", "admin@acme-cancel.com"));
        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();

        TenantStatusResponse statusResponse = tenantLifecycleService.updateStatus(
                tenantId, new UpdateTenantStatusRequest(TenantStatus.CANCELLED, "business_closed"));

        assertThat(statusResponse.previousStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(statusResponse.currentStatus()).isEqualTo(TenantStatus.CANCELLED);

        Tenant stored = tenantRepository.findById(tenantId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TenantStatus.CANCELLED);

        Subscription subscription = subscriptionRepository.findByTenantIdAndStatus(
                tenantId, SubscriptionStatus.ACTIVE).orElse(null);
        assertThat(subscription).isNull();

        List<Subscription> allSubscriptions = subscriptionRepository.findAll().stream()
                .filter(s -> s.getTenantId().equals(tenantId)).toList();
        assertThat(allSubscriptions).hasSize(1);
        assertThat(allSubscriptions.get(0).getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(allSubscriptions.get(0).getCancelledAt()).isNotNull();

        List<ApiKey> activeKeys = apiKeyRepository.findAll().stream()
                .filter(k -> k.getTenantId().equals(tenantId) && k.getRevokedAt() == null).toList();
        assertThat(activeKeys).isEmpty();

        List<TenantMember> activeMembers = tenantMemberRepository.findAll().stream()
                .filter(m -> m.getTenantId().equals(tenantId) && m.getIsActive()).toList();
        assertThat(activeMembers).isEmpty();

        List<AuditLog> logs = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        assertThat(logs).anyMatch(log -> log.getAction().equals("TENANT_CANCELLED"));
    }

    @Test
    void suspendsActiveTenant() {
        Plan plan = activePlan();
        CreateTenantResponse response = tenantService.create(
                request("Acme Suspend", plan.getId(), "sub_suspend_1", "admin@acme-suspend.com"));
        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();

        TenantStatusResponse statusResponse = tenantLifecycleService.updateStatus(
                tenantId, new UpdateTenantStatusRequest(TenantStatus.SUSPENDED, "payment_overdue"));

        assertThat(statusResponse.previousStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(statusResponse.currentStatus()).isEqualTo(TenantStatus.SUSPENDED);

        Tenant stored = tenantRepository.findById(tenantId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(stored.getSuspendedAt()).isNotNull();
        assertThat(stored.getSuspendedReason()).isEqualTo("payment_overdue");

        Subscription subscription = subscriptionRepository.findByTenantIdAndStatus(
                tenantId, SubscriptionStatus.PAST_DUE).orElse(null);
        assertThat(subscription).isNotNull();

        List<ApiKey> activeKeys = apiKeyRepository.findAll().stream()
                .filter(k -> k.getTenantId().equals(tenantId) && k.getRevokedAt() == null).toList();
        assertThat(activeKeys).isEmpty();

        List<TenantMember> activeMembers = tenantMemberRepository.findAll().stream()
                .filter(m -> m.getTenantId().equals(tenantId) && m.getIsActive()).toList();
        assertThat(activeMembers).isEmpty();

        List<AuditLog> logs = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        assertThat(logs).anyMatch(log -> log.getAction().equals("TENANT_SUSPENDED"));
    }

    @Test
    void reinstatesSuspendedTenant() {
        Plan plan = activePlan();
        CreateTenantResponse response = tenantService.create(
                request("Acme Reinstate", plan.getId(), "sub_reinstate_1", "admin@acme-reinstate.com"));
        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();

        tenantLifecycleService.updateStatus(
                tenantId, new UpdateTenantStatusRequest(TenantStatus.SUSPENDED, "payment_issue"));

        TenantStatusResponse statusResponse = tenantLifecycleService.updateStatus(
                tenantId, new UpdateTenantStatusRequest(TenantStatus.ACTIVE, null));

        assertThat(statusResponse.previousStatus()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(statusResponse.currentStatus()).isEqualTo(TenantStatus.ACTIVE);

        Tenant stored = tenantRepository.findById(tenantId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(stored.getSuspendedAt()).isNull();
        assertThat(stored.getSuspendedReason()).isNull();

        Subscription subscription = subscriptionRepository.findByTenantIdAndStatus(
                tenantId, SubscriptionStatus.ACTIVE).orElse(null);
        assertThat(subscription).isNotNull();

        List<AuditLog> logs = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        assertThat(logs).anyMatch(log -> log.getAction().equals("TENANT_REINSTATED"));
    }

    @Test
    void rejectsTransitionFromCancelledToActive() {
        Plan plan = activePlan();
        CreateTenantResponse response = tenantService.create(
                request("Acme Cancelled", plan.getId(), "sub_cancelled_1", "admin@acme-cancelled.com"));
        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();

        tenantLifecycleService.updateStatus(
                tenantId, new UpdateTenantStatusRequest(TenantStatus.CANCELLED, "done"));

        assertThatThrownBy(() -> tenantLifecycleService.updateStatus(
                tenantId, new UpdateTenantStatusRequest(TenantStatus.ACTIVE, null)))
                .isInstanceOf(EstadoTransicionInvalidoException.class);
    }

    @Test
    void rejectsTransitionFromCancelledToSuspended() {
        Plan plan = activePlan();
        CreateTenantResponse response = tenantService.create(
                request("Acme Cancelled2", plan.getId(), "sub_cancelled_2", "admin@acme-cancelled2.com"));
        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();

        tenantLifecycleService.updateStatus(
                tenantId, new UpdateTenantStatusRequest(TenantStatus.CANCELLED, "done"));

        assertThatThrownBy(() -> tenantLifecycleService.updateStatus(
                tenantId, new UpdateTenantStatusRequest(TenantStatus.SUSPENDED, "try")))
                .isInstanceOf(EstadoTransicionInvalidoException.class);
    }

    @Test
    void rejectsTransitionFromActiveToActive() {
        Plan plan = activePlan();
        CreateTenantResponse response = tenantService.create(
                request("Acme Same", plan.getId(), "sub_same_1", "admin@acme-same.com"));
        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();

        TenantStatusResponse statusResponse = tenantLifecycleService.updateStatus(
                tenantId, new UpdateTenantStatusRequest(TenantStatus.ACTIVE, null));

        assertThat(statusResponse.previousStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(statusResponse.currentStatus()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void rejectsTransitionFromSuspendedToSuspended() {
        Plan plan = activePlan();
        CreateTenantResponse response = tenantService.create(
                request("Acme Suspended", plan.getId(), "sub_suspended_1", "admin@acme-suspended.com"));
        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();

        tenantLifecycleService.updateStatus(
                tenantId, new UpdateTenantStatusRequest(TenantStatus.SUSPENDED, "reason"));

        TenantStatusResponse statusResponse = tenantLifecycleService.updateStatus(
                tenantId, new UpdateTenantStatusRequest(TenantStatus.SUSPENDED, "another"));

        assertThat(statusResponse.previousStatus()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(statusResponse.currentStatus()).isEqualTo(TenantStatus.SUSPENDED);
    }

    @Test
    void cancelsSuspendedTenant() {
        Plan plan = activePlan();
        CreateTenantResponse response = tenantService.create(
                request("Acme SuspendThenCancel", plan.getId(), "sub_stc_1", "admin@acme-stc.com"));
        tenantId = response.tenant().id();
        schemaName = response.tenant().schemaName();

        tenantLifecycleService.updateStatus(
                tenantId, new UpdateTenantStatusRequest(TenantStatus.SUSPENDED, "payment_issue"));

        TenantStatusResponse statusResponse = tenantLifecycleService.updateStatus(
                tenantId, new UpdateTenantStatusRequest(TenantStatus.CANCELLED, "gave_up"));

        assertThat(statusResponse.previousStatus()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(statusResponse.currentStatus()).isEqualTo(TenantStatus.CANCELLED);

        Tenant stored = tenantRepository.findById(tenantId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TenantStatus.CANCELLED);
    }

    @Test
    void rejectsTransitionFromPendingProvisioningToActive() {
        Plan plan = activePlan();
        CreateTenantRequest createRequest = new CreateTenantRequest(
                "Acme Pending Active",
                plan.getId(),
                new CreateTenantRequest.TenantAdminDto("sub_pending_1", "admin@acme-pending.com", "Admin"),
                new CreateTenantRequest.TenantIdentityProviderDto(
                        "https://idp.acme.com", "https://idp.acme.com/.well-known/jwks.json",
                        "https://api.acme.com", null, null));

        Tenant tenant = new Tenant();
        tenant.setName("Acme Pending Active");
        tenant.setSchemaName("mv_acme_pending_active");
        tenant.setStatus(TenantStatus.PENDING_PROVISIONING);
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();
        schemaName = tenant.getSchemaName();

        assertThatThrownBy(() -> tenantLifecycleService.updateStatus(
                tenantId, new UpdateTenantStatusRequest(TenantStatus.ACTIVE, null)))
                .isInstanceOf(EstadoTransicionInvalidoException.class);
    }

    @Test
    void rejectsUnknownTenant() {
        assertThatThrownBy(() -> tenantLifecycleService.updateStatus(
                UUID.randomUUID(), new UpdateTenantStatusRequest(TenantStatus.CANCELLED, "test")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    private Plan activePlan() {
        return planRepository.findAll().stream().filter(Plan::getIsActive).findFirst().orElseThrow();
    }

    private CreateTenantRequest request(String name, UUID planId, String subject, String email) {
        return new CreateTenantRequest(name, planId,
                new CreateTenantRequest.TenantAdminDto(subject, email, "Admin"),
                new CreateTenantRequest.TenantIdentityProviderDto(
                        "https://idp.acme.com", "https://idp.acme.com/.well-known/jwks.json",
                        "https://api.acme.com", null, null));
    }
}
