package dev.achiri.multivault.apikey.service;

import dev.achiri.multivault.apikey.model.ApiKey;
import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.apikey.repository.ApiKeyRepository;
import dev.achiri.multivault.infrastructure.security.apikey.Scopes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String KEY_PREFIX_LABEL = "mv_live_";
    private static final int RANDOM_CHARS = 40;
    private static final int KEY_PREFIX_LENGTH = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyHasher apiKeyHasher;

    @Transactional
    public ApiKeyResult createInitial(UUID tenantId, UUID createdByUserId) {
        String rawKey = KEY_PREFIX_LABEL + randomHex();
        ApiKey apiKey = new ApiKey();
        apiKey.setTenantId(tenantId);
        apiKey.setName("Initial Admin Key");
        apiKey.setKeyPrefix(rawKey.substring(0, KEY_PREFIX_LENGTH));
        apiKey.setKeyHash(apiKeyHasher.sha256Hex(rawKey));
        apiKey.setKeyType(ApiKeyType.SERVICE);
        apiKey.setScopes(List.of(Scopes.WILDCARD));
        apiKey.setCreatedByUserId(createdByUserId);
        apiKeyRepository.save(apiKey);
        return new ApiKeyResult(apiKey.getId(), apiKey.getName(), apiKey.getKeyPrefix(), apiKey.getKeyType(), rawKey);
    }

    private String randomHex() {
        byte[] bytes = new byte[RANDOM_CHARS / 2];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
