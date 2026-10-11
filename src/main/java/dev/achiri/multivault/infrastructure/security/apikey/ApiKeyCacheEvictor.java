package dev.achiri.multivault.infrastructure.security.apikey;

import dev.achiri.multivault.apikey.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ApiKeyCacheEvictor {

    private static final String CACHE_NAME = "apiKeys";

    private final CacheManager cacheManager;
    private final ApiKeyRepository apiKeyRepository;

    public void evictByHash(String hash) {
        if (hash == null) {
            return;
        }
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.evict(hash);
        }
    }

    public void evictByTenant(UUID tenantId) {
        if (tenantId == null) {
            return;
        }
        List<String> activeHashes = apiKeyRepository.findActiveHashesByTenantId(tenantId);
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            for (String hash : activeHashes) {
                cache.evict(hash);
            }
        }
    }
}
