package dev.achiri.multivault.infrastructure.security.apikey;

import dev.achiri.multivault.apikey.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticator {

    private final ApiKeyRepository apiKeyRepository;

    @Cacheable(cacheNames = "apiKeys", key = "#hash")
    public ApiKeyIdentity findValidByHash(String hash) {
        return apiKeyRepository.findByKeyHashAndRevokedAtIsNull(hash)
                .map(key -> new ApiKeyIdentity(
                        key.getId(),
                        key.getTenantId(),
                        key.getName(),
                        key.getKeyType(),
                        key.getScopes(),
                        key.getExpiresAt() != null ? key.getExpiresAt().getEpochSecond() : 0))
                .orElse(null);
    }
}
