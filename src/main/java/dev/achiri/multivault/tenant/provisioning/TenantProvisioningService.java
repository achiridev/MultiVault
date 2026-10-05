package dev.achiri.multivault.tenant.provisioning;

import dev.achiri.multivault.apikey.service.ApiKeyResult;
import dev.achiri.multivault.apikey.service.ApiKeyService;
import dev.achiri.multivault.audit.event.AuditEvent;
import dev.achiri.multivault.audit.event.AuditEventPublisher;
import dev.achiri.multivault.audit.model.ActorType;
import dev.achiri.multivault.common.exception.RecursoNoEncontradoException;
import dev.achiri.multivault.plan.model.Plan;
import dev.achiri.multivault.plan.repository.PlanRepository;
import dev.achiri.multivault.subscription.mapper.SubscriptionMapper;
import dev.achiri.multivault.subscription.model.Subscription;
import dev.achiri.multivault.subscription.repository.SubscriptionRepository;
import dev.achiri.multivault.tenant.dto.CreateTenantRequest;
import dev.achiri.multivault.tenant.mapper.TenantIdentityProviderMapper;
import dev.achiri.multivault.tenant.mapper.TenantMapper;
import dev.achiri.multivault.tenant.mapper.TenantMemberMapper;
import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.model.TenantIdentityProvider;
import dev.achiri.multivault.tenant.model.TenantMember;
import dev.achiri.multivault.tenant.model.TenantStatus;
import dev.achiri.multivault.tenant.model.TenantUsage;
import dev.achiri.multivault.tenant.repository.TenantIdentityProviderRepository;
import dev.achiri.multivault.tenant.repository.TenantMemberRepository;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import dev.achiri.multivault.tenant.repository.TenantUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantUsageRepository tenantUsageRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final TenantIdentityProviderRepository tenantIdentityProviderRepository;

    private final TenantMapper tenantMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final TenantIdentityProviderMapper tenantIdentityProviderMapper;

    private final ApiKeyService apiKeyService;
    private final AuditEventPublisher auditEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PlanRepository planRepository;

    @Transactional
    public OnboardingResult initialize(CreateTenantRequest request, Plan plan, String schemaName) {
        Tenant tenant = tenantMapper.toEntity(request);
        tenant.setSchemaName(schemaName);
        tenant.setCurrentPlanId(plan.getId());
        tenantRepository.save(tenant);

        Subscription subscription = subscriptionMapper.toEntity(request);
        subscription.setTenantId(tenant.getId());
        subscriptionRepository.save(subscription);

        TenantUsage usage = new TenantUsage();
        usage.setTenantId(tenant.getId());
        tenantUsageRepository.save(usage);

        TenantMember admin = tenantMemberMapper.toEntity(request.admin());
        admin.setTenantId(tenant.getId());
        tenantMemberRepository.save(admin);

        TenantIdentityProvider identityProvider = tenantIdentityProviderMapper.toEntity(request.identityProvider());
        identityProvider.setTenantId(tenant.getId());
        tenantIdentityProviderRepository.save(identityProvider);

        return new OnboardingResult(tenant, subscription, admin, identityProvider);
    }

    @Transactional
    public void markProvisioningFailed(UUID tenantId, String reason) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("tenant", tenantId));
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenant.setSuspendedReason(reason);
        tenant.setSuspendedAt(Instant.now());
    }

    @Transactional
    public ActivationResult activate(OnboardingResult onboarding, Plan plan) {
        Tenant tenant = tenantRepository.findById(onboarding.tenant().getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("tenant", onboarding.tenant().getId()));
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setCurrentPlanId(plan.getId());

        ApiKeyResult apiKey = apiKeyService.createInitial(tenant.getId(), onboarding.admin().getId());

        publishTenantCreated(tenant, plan, onboarding.admin());

        return new ActivationResult(tenant, apiKey);
    }

    @Transactional
    public ApiKeyResult issueInitialCredentialsAndRequestProvisioning(UUID tenantId, UUID adminId,
                                                                     String schemaName, UUID planId) {
        ApiKeyResult apiKey = apiKeyService.createInitial(tenantId, adminId);
        applicationEventPublisher.publishEvent(
                new ProvisioningRequestedEvent(tenantId, schemaName, planId));
        return apiKey;
    }

    @Transactional
    public boolean activateIfPending(UUID tenantId, UUID planId) {
        if (tenantRepository.activateIfPending(tenantId, planId,
                TenantStatus.PENDING_PROVISIONING, TenantStatus.ACTIVE) == 0) {
            return false;
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("tenant", tenantId));
        publishTenantCreated(tenant, planRepository.findById(planId)
                .orElseThrow(() -> new RecursoNoEncontradoException("plan", planId)), null);
        return true;
    }

    private void publishTenantCreated(Tenant tenant, Plan plan, TenantMember admin) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("tenant_name", tenant.getName());
        metadata.put("plan_id", plan.getId().toString());
        metadata.put("provisioning", "async");
        if (admin != null) {
            metadata.put("admin_email", admin.getEmail());
        }
        auditEventPublisher.publish(AuditEvent.builder()
                .tenantId(tenant.getId())
                .actorType(ActorType.SYSTEM)
                .action("TENANT_CREATED")
                .resourceType("tenant")
                .resourceId(tenant.getId())
                .metadata(metadata)
                .build());
    }
}
