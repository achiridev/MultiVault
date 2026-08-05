package dev.achiri.multivault.tenant.service;

import dev.achiri.multivault.common.exception.RecursoNoEncontradoException;
import dev.achiri.multivault.common.util.SlugUtils;
import dev.achiri.multivault.plan.model.Plan;
import dev.achiri.multivault.plan.repository.PlanRepository;
import dev.achiri.multivault.subscription.model.Subscription;
import dev.achiri.multivault.subscription.model.SubscriptionStatus;
import dev.achiri.multivault.subscription.repository.SubscriptionRepository;
import dev.achiri.multivault.tenant.dto.CreateOrganizationRequest;
import dev.achiri.multivault.tenant.dto.CreateOrganizationResponse;
import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.model.TenantIdentityProvider;
import dev.achiri.multivault.tenant.model.TenantMember;
import dev.achiri.multivault.tenant.repository.TenantIdentityProviderRepository;
import dev.achiri.multivault.tenant.repository.TenantMemberRepository;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantService {
    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantIdentityProviderRepository tenantIdentityProviderRepository;
    private final TenantMemberRepository tenantMemberRepository;

    @Transactional
    public CreateOrganizationResponse create(CreateOrganizationRequest request) {
        Plan plan = planRepository.findById(request.planId())
                .filter(Plan::getIsActive)
                .orElseThrow(() -> new RecursoNoEncontradoException("plan", request.planId()));

        Tenant tenant = new Tenant();
        tenant.setName(request.name());
        tenant.setSchemaName(generateSchemaName(request.name()));
        tenant.setCurrentPlanId(plan.getId());
        tenantRepository.save(tenant);

        Subscription subscription = new Subscription();
        subscription.setTenantId(tenant.getId());
        subscription.setPlanId(plan.getId());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartsAt(Instant.now());
        subscriptionRepository.save(subscription);

        TenantIdentityProvider identityProvider = new TenantIdentityProvider();
        identityProvider.setTenantId(tenant.getId());
        identityProvider.setIssuer(request.identityProvider().issuer());
        identityProvider.setJwksUri(request.identityProvider().jwksUri());
        identityProvider.setAudience(request.identityProvider().audience());
        identityProvider.setAllowedAlgorithms(
                request.identityProvider().allowedAlgorithms() == null
                        ? List.of("RS256")
                        : request.identityProvider().allowedAlgorithms());
        identityProvider.setClockSkewSeconds(
                request.identityProvider().clockSkewSeconds() == null
                        ? 60
                        : request.identityProvider().clockSkewSeconds());
        tenantIdentityProviderRepository.save(identityProvider);

        TenantMember admin = new TenantMember();
        admin.setTenantId(tenant.getId());
        admin.setSubject(request.admin().subject());
        admin.setEmail(request.admin().email());
        admin.setDisplayName(request.admin().displayName());
        tenantMemberRepository.save(admin);

        return buildResponse(tenant, plan, subscription, admin, identityProvider);
    }

    private CreateOrganizationResponse buildResponse(
            Tenant tenant,
            Plan plan,
            Subscription subscription,
            TenantMember admin,
            TenantIdentityProvider identityProvider) {
        return new CreateOrganizationResponse(
                new CreateOrganizationResponse.TenantDto(
                        tenant.getId(), tenant.getName(), tenant.getSchemaName(), tenant.getStatus()),
                new CreateOrganizationResponse.SubscriptionDto(
                        subscription.getId(), subscription.getPlanId(),
                        plan.getCode().name(), subscription.getStatus().name()),
                new CreateOrganizationResponse.AdminDto(
                        admin.getId(), admin.getSubject(), admin.getEmail(), admin.getDisplayName()),
                new CreateOrganizationResponse.IdentityProviderDto(
                        identityProvider.getIssuer(), identityProvider.getJwksUri(),
                        identityProvider.getAudience(), identityProvider.getAllowedAlgorithms(),
                        identityProvider.getClockSkewSeconds()));
    }

    private String generateSchemaName(String name) {
        return "mv_" + SlugUtils.toSlug(name);
    }
}
