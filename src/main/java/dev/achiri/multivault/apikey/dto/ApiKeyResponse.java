package dev.achiri.multivault.apikey.dto;

import dev.achiri.multivault.apikey.model.ApiKeyType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        ApiKeyType keyType,
        List<String> scopes,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        Instant revokedAt,
        boolean expired
) {
}
