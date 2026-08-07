package dev.achiri.multivault.tenant.dto;

import dev.achiri.multivault.tenant.model.TenantStatus;

import java.util.List;
import java.util.UUID;

public record CreateOrganizationResponse(
        TenantDto tenant,
        SubscriptionDto subscription,
        AdminDto admin,
        IdentityProviderDto identityProvider,
        ApiKeyDto apiKey
) {
    public record TenantDto(
            UUID id,
            String name,
            String schemaName,
            TenantStatus status
    ) {
    }

    public record SubscriptionDto(
            UUID id,
            UUID planId,
            String planCode,
            String status
    ) {
    }

    public record AdminDto(
            UUID memberId,
            String subject,
            String email,
            String displayName
    ) {
    }

    public record IdentityProviderDto(
            String issuer,
            String jwksUri,
            String audience,
            List<String> allowedAlgorithms,
            Integer clockSkewSeconds
    ) {
    }

    public record ApiKeyDto(
            UUID id,
            String name,
            String keyPrefix,
            String keyType,
            String key
    ) {
    }
}
