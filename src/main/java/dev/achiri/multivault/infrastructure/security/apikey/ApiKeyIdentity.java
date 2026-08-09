package dev.achiri.multivault.infrastructure.security.apikey;

import dev.achiri.multivault.apikey.model.ApiKeyType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApiKeyIdentity(
        UUID keyId,
        UUID tenantId,
        String name,
        ApiKeyType keyType,
        List<String> scopes,
        long expiresAtEpochSecond
) {

    public boolean isExpired() {
        return expiresAtEpochSecond > 0 && expiresAtEpochSecond < Instant.now().getEpochSecond();
    }
}
