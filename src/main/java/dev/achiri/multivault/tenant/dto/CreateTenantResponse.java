package dev.achiri.multivault.tenant.dto;

import dev.achiri.multivault.tenant.model.TenantStatus;

import java.util.List;
import java.util.UUID;

public record CreateTenantResponse(
        TenantDto tenant,
        SubscriptionDto subscription,
        TenantAdminDto admin,
        TenantIdentityProviderDto identityProvider,
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

    public record TenantAdminDto(
            UUID memberId,
            String subject,
            String email,
            String displayName
    ) {
    }

    public record TenantIdentityProviderDto(
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
