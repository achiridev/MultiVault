package dev.achiri.multivault.infrastructure.security.apikey;

import dev.achiri.multivault.apikey.model.ApiKeyType;

import java.util.UUID;

public record ApiKeyPrincipal(
        UUID keyId,
        UUID tenantId,
        String name,
        ApiKeyType keyType
) {
}
