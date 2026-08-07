package dev.achiri.multivault.tenant.service;

import dev.achiri.multivault.apikey.service.ApiKeyResult;
import dev.achiri.multivault.common.exception.RecursoDuplicadoException;
import dev.achiri.multivault.common.exception.RecursoNoEncontradoException;
import dev.achiri.multivault.common.exception.TenantProvisioningException;
import dev.achiri.multivault.common.util.SlugUtils;
import dev.achiri.multivault.infrastructure.persistence.tenant.TenantProvisioningFailedEvent;
import dev.achiri.multivault.infrastructure.persistence.tenant.TenantSchemaProvisioner;
import dev.achiri.multivault.plan.model.Plan;
import dev.achiri.multivault.plan.repository.PlanRepository;
import dev.achiri.multivault.subscription.mapper.SubscriptionMapper;
import dev.achiri.multivault.tenant.dto.CreateOrganizationRequest;
import dev.achiri.multivault.tenant.dto.CreateOrganizationResponse;
import dev.achiri.multivault.tenant.mapper.TenantIdentityProviderMapper;
import dev.achiri.multivault.tenant.mapper.TenantMapper;
import dev.achiri.multivault.tenant.mapper.TenantMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private static final String SCHEMA_PREFIX = "mv_";
    private static final int SCHEMA_MAX_LENGTH = 63;

    private final TenantProvisioningService tenantProvisioningService;
    private final TenantSchemaProvisioner tenantSchemaProvisioner;
    private final ApplicationEventPublisher applicationEventPublisher;

    private final PlanRepository planRepository;

    private final TenantMapper tenantMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final TenantIdentityProviderMapper tenantIdentityProviderMapper;

    public CreateOrganizationResponse create(CreateOrganizationRequest request) {
        Plan plan = planRepository.findById(request.planId())
                .filter(Plan::getIsActive)
                .orElseThrow(() -> new RecursoNoEncontradoException("plan", request.planId()));

        String schemaName = generateSchemaName(request.name());

        OnboardingResult onboarding = initialize(request, plan, schemaName);
        provisionSchema(onboarding.tenant().getId(), schemaName);
        ActivationResult activation = tenantProvisioningService.activate(onboarding, plan);

        return new CreateOrganizationResponse(
                tenantMapper.toDto(activation.tenant()),
                subscriptionMapper.toDto(onboarding.subscription(), plan),
                tenantMemberMapper.toDto(onboarding.admin()),
                onboarding.identityProvider() == null
                        ? null
                        : tenantIdentityProviderMapper.toDto(onboarding.identityProvider()),
                toApiKeyDto(activation.apiKey()));
    }

    private OnboardingResult initialize(CreateOrganizationRequest request, Plan plan, String schemaName) {
        try {
            return tenantProvisioningService.initialize(request, plan, schemaName);
        } catch (DataIntegrityViolationException e) {
            throw new RecursoDuplicadoException("tenant", schemaName);
        }
    }

    private void provisionSchema(UUID tenantId, String schemaName) {
        try {
            tenantSchemaProvisioner.provision(schemaName);
        } catch (RuntimeException e) {
            String reason = "schema_provisioning_failed";
            tenantProvisioningService.markProvisioningFailed(tenantId, reason);
            applicationEventPublisher.publishEvent(new TenantProvisioningFailedEvent(tenantId, schemaName, reason));
            throw new TenantProvisioningException(e);
        }
    }

    private CreateOrganizationResponse.ApiKeyDto toApiKeyDto(ApiKeyResult apiKey) {
        return new CreateOrganizationResponse.ApiKeyDto(
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
}
