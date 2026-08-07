package dev.achiri.multivault.tenant.service;

import dev.achiri.multivault.audit.event.AuditEvent;
import dev.achiri.multivault.audit.event.AuditEventPublisher;
import dev.achiri.multivault.audit.model.ActorType;
import dev.achiri.multivault.common.exception.RecursoNoEncontradoException;
import dev.achiri.multivault.common.util.SlugUtils;
import dev.achiri.multivault.plan.model.Plan;
import dev.achiri.multivault.plan.repository.PlanRepository;
import dev.achiri.multivault.subscription.mapper.SubscriptionMapper;
import dev.achiri.multivault.subscription.model.Subscription;
import dev.achiri.multivault.subscription.repository.SubscriptionRepository;
import dev.achiri.multivault.tenant.dto.CreateOrganizationRequest;
import dev.achiri.multivault.tenant.dto.CreateOrganizationResponse;
import dev.achiri.multivault.tenant.mapper.TenantIdentityProviderMapper;
import dev.achiri.multivault.tenant.mapper.TenantMapper;
import dev.achiri.multivault.tenant.mapper.TenantMemberMapper;
import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.model.TenantIdentityProvider;
import dev.achiri.multivault.tenant.model.TenantMember;
import dev.achiri.multivault.tenant.repository.TenantIdentityProviderRepository;
import dev.achiri.multivault.tenant.repository.TenantMemberRepository;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantIdentityProviderRepository tenantIdentityProviderRepository;
    private final TenantMemberRepository tenantMemberRepository;

    private final TenantMapper tenantMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final TenantIdentityProviderMapper tenantIdentityProviderMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final AuditEventPublisher auditEventPublisher;

    @Transactional
    public CreateOrganizationResponse create(CreateOrganizationRequest request) {
        Plan plan = planRepository.findById(request.planId())
                .filter(Plan::getIsActive)
                .orElseThrow(() -> new RecursoNoEncontradoException("plan", request.planId()));

        Tenant tenant = tenantMapper.toEntity(request);
        tenant.setSchemaName(generateSchemaName(request.name()));
        tenantRepository.save(tenant);

        Subscription subscription = subscriptionMapper.toEntity(request);
        subscription.setTenantId(tenant.getId());
        subscriptionRepository.save(subscription);

        TenantIdentityProvider identityProvider = tenantIdentityProviderMapper.toEntity(request.identityProvider());
        identityProvider.setTenantId(tenant.getId());
        tenantIdentityProviderRepository.save(identityProvider);

        TenantMember admin = tenantMemberMapper.toEntity(request.admin());
        admin.setTenantId(tenant.getId());
        tenantMemberRepository.save(admin);

        auditEventPublisher.publish(AuditEvent.builder()
                .tenantId(tenant.getId())
                .actorType(ActorType.SYSTEM)
                .action("TENANT_CREATED")
                .resourceType("tenant")
                .resourceId(tenant.getId())
                .metadata(Map.of("plan", plan.getCode().name()))
                .build());

        return new CreateOrganizationResponse(
                tenantMapper.toDto(tenant),
                subscriptionMapper.toDto(subscription, plan),
                tenantMemberMapper.toDto(admin),
                tenantIdentityProviderMapper.toDto(identityProvider));
    }

    private String generateSchemaName(String name) {
        return "mv_" + SlugUtils.toSlug(name);
    }
}
