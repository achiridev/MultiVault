package dev.achiri.multivault.apikey.service;

import dev.achiri.multivault.apikey.dto.ApiKeyIssuedResponse;
import dev.achiri.multivault.apikey.dto.ApiKeyResponse;
import dev.achiri.multivault.apikey.dto.CreateApiKeyRequest;
import dev.achiri.multivault.apikey.mapper.ApiKeyMapper;
import dev.achiri.multivault.apikey.model.ApiKey;
import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.apikey.repository.ApiKeyRepository;
import dev.achiri.multivault.audit.event.AuditContext;
import dev.achiri.multivault.audit.event.AuditEvent;
import dev.achiri.multivault.audit.event.AuditEventPublisher;
import dev.achiri.multivault.common.exception.EstadoTransicionInvalidoException;
import dev.achiri.multivault.common.exception.RecursoDuplicadoException;
import dev.achiri.multivault.common.exception.RecursoNoEncontradoException;
import dev.achiri.multivault.infrastructure.security.Scopes;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyCacheEvictor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final ApiKeyScopeValidator apiKeyScopeValidator;
    private final ApiKeyMapper apiKeyMapper;
    private final ApiKeyCacheEvictor apiKeyCacheEvictor;
    private final AuditEventPublisher auditEventPublisher;

    @Transactional
    public ApiKeyResult createInitial(UUID tenantId, UUID createdByUserId) {
        String rawKey = KEY_PREFIX_LABEL + randomHex();
        ApiKey apiKey = new ApiKey();
        apiKey.setTenantId(tenantId);
        apiKey.setName("Tenant Admin Key");
        apiKey.setKeyPrefix(rawKey.substring(0, KEY_PREFIX_LENGTH));
        apiKey.setKeyHash(apiKeyHasher.sha256Hex(rawKey));
        apiKey.setKeyType(ApiKeyType.SERVICE);
        apiKey.setExpiresAt(null);
        apiKey.setScopes(Scopes.ADMIN);
        apiKey.setCreatedByUserId(createdByUserId);
        apiKeyRepository.save(apiKey);
        return new ApiKeyResult(apiKey.getId(), apiKey.getName(), apiKey.getKeyPrefix(), apiKey.getKeyType(), rawKey);
    }

    @Transactional
    public ApiKeyIssuedResponse create(CreateApiKeyRequest request, UUID tenantId, AuditContext actor) {
        List<String> validatedScopes = apiKeyScopeValidator.validateAndNormalize(request.scopes());

        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("expiresAt debe ser una fecha futura");
        }

        if (validatedScopes.contains(Scopes.API_KEYS_MANAGE)
                && apiKeyRepository.countActiveWithScope(tenantId, Scopes.API_KEYS_MANAGE) > 0) {
            throw new RecursoDuplicadoException("Ya existe una key de administración activa para este tenant, revócala primero");
        }

        String rawKey = KEY_PREFIX_LABEL + randomHex();
        ApiKey apiKey = new ApiKey();
        apiKey.setTenantId(tenantId);
        apiKey.setName(request.name().trim());
        apiKey.setKeyPrefix(rawKey.substring(0, KEY_PREFIX_LENGTH));
        apiKey.setKeyHash(apiKeyHasher.sha256Hex(rawKey));
        apiKey.setKeyType(request.keyType());
        apiKey.setScopes(validatedScopes);
        apiKey.setExpiresAt(request.expiresAt());
        apiKey.setCreatedByUserId(actor.actorUserId());
        apiKey.setCreatedByApiKeyId(actor.apiKeyId());
        apiKeyRepository.save(apiKey);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", apiKey.getName());
        metadata.put("key_type", apiKey.getKeyType().name());
        metadata.put("scopes", apiKey.getScopes());
        metadata.put("expires_at", apiKey.getExpiresAt() != null ? apiKey.getExpiresAt().toString() : "never");

        if (actor.apiKeyId() != null) {
            apiKeyRepository.findById(actor.apiKeyId())
                    .ifPresent(creator -> metadata.put("created_by_key_prefix", creator.getKeyPrefix()));
        }

        auditEventPublisher.publish(AuditEvent.builder()
                .tenantId(tenantId)
                .actorUserId(actor.actorUserId())
                .apiKeyId(actor.apiKeyId())
                .actorType(actor.actorType())
                .action("API_KEY_CREATED")
                .resourceType("api_key")
                .resourceId(apiKey.getId())
                .ipAddress(actor.ipAddress())
                .userAgent(actor.userAgent())
                .metadata(metadata)
                .build());

        return apiKeyMapper.toIssuedResponse(apiKey, rawKey);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listByTenant(UUID tenantId, boolean includeRevoked) {
        List<ApiKey> keys = includeRevoked
                ? apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                : apiKeyRepository.findByTenantIdAndRevokedAtIsNullOrderByCreatedAtDesc(tenantId);
        return apiKeyMapper.toResponseList(keys);
    }

    @Transactional
    public void revoke(UUID tenantId, UUID keyId, AuditContext actor) {
        ApiKey apiKey = apiKeyRepository.findByIdAndTenantId(keyId, tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("api_key", keyId));

        if (apiKey.getRevokedAt() != null) {
            return;
        }

        if (apiKey.getScopes().contains(Scopes.API_KEYS_MANAGE)
                && apiKeyRepository.countActiveWithScope(tenantId, Scopes.API_KEYS_MANAGE) <= 1) {
            throw new EstadoTransicionInvalidoException("No se puede revocar la última key de administración activa del tenant");
        }

        apiKey.setRevokedAt(Instant.now());
        apiKeyRepository.save(apiKey);
        apiKeyCacheEvictor.evictByHash(apiKey.getKeyHash());

        auditEventPublisher.publish(AuditEvent.builder()
                .tenantId(tenantId)
                .actorUserId(actor.actorUserId())
                .apiKeyId(actor.apiKeyId())
                .actorType(actor.actorType())
                .action("API_KEY_REVOKED")
                .resourceType("api_key")
                .resourceId(apiKey.getId())
                .ipAddress(actor.ipAddress())
                .userAgent(actor.userAgent())
                .metadata(Map.of(
                        "name", apiKey.getName(),
                        "revoked_scopes", apiKey.getScopes()
                ))
                .build());
    }

    private String randomHex() {
        byte[] bytes = new byte[RANDOM_CHARS / 2];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
