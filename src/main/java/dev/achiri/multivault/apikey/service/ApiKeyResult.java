package dev.achiri.multivault.apikey.service;

import dev.achiri.multivault.apikey.model.ApiKeyType;

import java.util.UUID;

public record ApiKeyResult(
        UUID id,
        String name,
        String keyPrefix,
        ApiKeyType keyType,
        String rawKey
) {
}
