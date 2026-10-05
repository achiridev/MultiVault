package dev.achiri.multivault.tenant.service;

import dev.achiri.multivault.apikey.service.ApiKeyResult;
import dev.achiri.multivault.audit.event.AuditEvent;
import dev.achiri.multivault.audit.event.AuditEventPublisher;
import dev.achiri.multivault.audit.model.ActorType;
import dev.achiri.multivault.common.exception.RecursoDuplicadoException;
import dev.achiri.multivault.common.exception.RecursoNoEncontradoException;
import dev.achiri.multivault.common.util.SlugUtils;
import dev.achiri.multivault.infrastructure.async.ProvisioningProperties;
import dev.achiri.multivault.infrastructure.security.jwt.jwks.JwksProvider;
import dev.achiri.multivault.plan.model.Plan;
import dev.achiri.multivault.plan.repository.PlanRepository;
import dev.achiri.multivault.subscription.mapper.SubscriptionMapper;
import dev.achiri.multivault.tenant.dto.CreateTenantRequest;
import dev.achiri.multivault.tenant.dto.CreateTenantResponse;
import dev.achiri.multivault.tenant.dto.UpdateTenantIdentityProviderRequest;
import dev.achiri.multivault.tenant.mapper.TenantIdentityProviderMapper;
import dev.achiri.multivault.tenant.mapper.TenantMapper;
import dev.achiri.multivault.tenant.mapper.TenantMemberMapper;
import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.model.TenantIdentityProvider;
import dev.achiri.multivault.tenant.provisioning.OnboardingResult;
import dev.achiri.multivault.tenant.provisioning.TenantProvisioningService;
import dev.achiri.multivault.tenant.repository.TenantIdentityProviderRepository;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private static final String SCHEMA_PREFIX = "mv_";
    private static final int SCHEMA_MAX_LENGTH = 63;

    private final TenantProvisioningService tenantProvisioningService;
    private final JwksProvider jwksProvider;
    private final ProvisioningProperties provisioningProperties;

    private final PlanRepository planRepository;
    private final TenantRepository tenantRepository;
    private final TenantIdentityProviderRepository tenantIdentityProviderRepository;
    private final AuditEventPublisher auditEventPublisher;

    private final TenantMapper tenantMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final TenantIdentityProviderMapper tenantIdentityProviderMapper;

    /**
     * El aprovisionamiento asíncrono deja el CREATE SCHEMA y el ciclo de Flyway fuera
     * del camino del request; ver ADR-0016.
     */
    public CreateTenantResponse create(CreateTenantRequest request) {
        Plan plan = planRepository.findById(request.planId())
                .filter(Plan::getIsActive)
                .orElseThrow(() -> new RecursoNoEncontradoException("plan", request.planId()));

        jwksProvider.probe(request.identityProvider().jwksUri());

        String schemaName = generateSchemaName(request.name());
        OnboardingResult onboarding = initialize(request, plan, schemaName);

        if (provisioningProperties.enabled()) {
            return enqueueProvisioning(onboarding, plan);
        }
        throw new IllegalStateException(
                "El aprovisionamiento asíncrono es obligatorio; habilita multivault.provisioning.enabled");
    }

    private OnboardingResult initialize(CreateTenantRequest request, Plan plan, String schemaName) {
        try {
            return tenantProvisioningService.initialize(request, plan, schemaName);
        } catch (DataIntegrityViolationException e) {
            throw new RecursoDuplicadoException("tenant", schemaName);
        }
    }

    private CreateTenantResponse enqueueProvisioning(OnboardingResult onboarding, Plan plan) {
        ApiKeyResult apiKey = tenantProvisioningService.issueInitialCredentialsAndRequestProvisioning(
                onboarding.tenant().getId(), onboarding.admin().getId(),
                onboarding.tenant().getSchemaName(), plan.getId());
        log.info("Tenant {} registrado y encolado para aprovisionamiento asíncrono",
                onboarding.tenant().getId());
        return new CreateTenantResponse(
                tenantMapper.toDto(onboarding.tenant()),
                subscriptionMapper.toDto(onboarding.subscription(), plan),
                tenantMemberMapper.toDto(onboarding.admin()),
                tenantIdentityProviderMapper.toDto(onboarding.identityProvider()),
                toApiKeyDto(apiKey));
    }

    private CreateTenantResponse.ApiKeyDto toApiKeyDto(ApiKeyResult apiKey) {
        return new CreateTenantResponse.ApiKeyDto(
                apiKey.id(),
                apiKey.name(),
                apiKey.keyPrefix(),
                apiKey.keyType().name(),
                apiKey.rawKey());
    }

    private String generateSchemaName(String name) {
        String slug = SlugUtils.toSlug(name);
        if (slug.isEmpty()) {
            throw new IllegalArgumentException("El nombre del tenant no genera un schema_name válido");
        }
        String schemaName = SCHEMA_PREFIX + slug;
        return schemaName.length() <= SCHEMA_MAX_LENGTH
                ? schemaName
                : schemaName.substring(0, SCHEMA_MAX_LENGTH);
    }

    @Transactional
    public CreateTenantResponse.TenantIdentityProviderDto updateIdentityProvider(
            UUID tenantId, UpdateTenantIdentityProviderRequest request) {

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("tenant", tenantId));

        jwksProvider.probe(request.jwksUri());

        TenantIdentityProvider identityProvider = tenantIdentityProviderMapper.toEntity(request);
        identityProvider.setTenantId(tenantId);
        tenantIdentityProviderRepository.save(identityProvider);

        auditEventPublisher.publish(AuditEvent.builder()
                .tenantId(tenantId)
                .actorType(ActorType.SYSTEM)
                .action("TENANT_IDENTITY_PROVIDER_UPDATED")
                .resourceType("tenant_identity_provider")
                .resourceId(tenantId)
                .metadata(Map.of("tenant_name", tenant.getName()))
                .build());

        return tenantIdentityProviderMapper.toDto(identityProvider);
    }
}