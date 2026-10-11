package dev.achiri.multivault.apikey;

import dev.achiri.multivault.apikey.dto.ApiKeyIssuedResponse;
import dev.achiri.multivault.apikey.dto.ApiKeyResponse;
import dev.achiri.multivault.apikey.dto.CreateApiKeyRequest;
import dev.achiri.multivault.apikey.mapper.ApiKeyMapper;
import dev.achiri.multivault.apikey.model.ApiKey;
import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.apikey.repository.ApiKeyRepository;
import dev.achiri.multivault.apikey.service.ApiKeyHasher;
import dev.achiri.multivault.apikey.service.ApiKeyResult;
import dev.achiri.multivault.apikey.service.ApiKeyScopeValidator;
import dev.achiri.multivault.apikey.service.ApiKeyService;
import dev.achiri.multivault.audit.event.AuditContext;
import dev.achiri.multivault.audit.event.AuditEvent;
import dev.achiri.multivault.audit.event.AuditEventPublisher;
import dev.achiri.multivault.audit.model.ActorType;
import dev.achiri.multivault.common.exception.EstadoTransicionInvalidoException;
import dev.achiri.multivault.common.exception.RecursoDuplicadoException;
import dev.achiri.multivault.infrastructure.security.Scopes;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyCacheEvictor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private ApiKeyHasher apiKeyHasher;

    @Mock
    private ApiKeyScopeValidator apiKeyScopeValidator;

    @Mock
    private ApiKeyMapper apiKeyMapper;

    @Mock
    private ApiKeyCacheEvictor apiKeyCacheEvictor;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @InjectMocks
    private ApiKeyService apiKeyService;

    @Test
    void generatesRawKeyShownOnceAndStoresOnlyHash() {
        UUID tenantId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(apiKeyHasher.sha256Hex(anyString())).thenAnswer(invocation ->
                sha256Hex(invocation.getArgument(0)));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey key = invocation.getArgument(0);
            key.setId(UUID.randomUUID());
            return key;
        });

        ApiKeyResult result = apiKeyService.createInitial(tenantId, memberId);

        assertThat(result.rawKey()).startsWith("mv_live_");
        assertThat(result.rawKey()).hasSize(48);
        assertThat(result.keyPrefix()).isEqualTo(result.rawKey().substring(0, 12));

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKey saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Tenant Admin Key");
        assertThat(saved.getExpiresAt()).isNull();
        assertThat(saved.getKeyHash()).isEqualTo(sha256Hex(result.rawKey()));
        assertThat(saved.getKeyHash()).isNotEqualTo(result.rawKey());
        assertThat(saved.getKeyType()).isEqualTo(ApiKeyType.SERVICE);
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getCreatedByUserId()).isEqualTo(memberId);
        assertThat(saved.getScopes()).containsExactlyElementsOf(Scopes.ADMIN);
    }

    @Test
    void createRejectsPastExpirationDate() {
        UUID tenantId = UUID.randomUUID();
        CreateApiKeyRequest request = new CreateApiKeyRequest(
                "Test Key",
                ApiKeyType.STANDARD,
                List.of(Scopes.DOCUMENTS_READ),
                Instant.now().minus(1, ChronoUnit.DAYS));
        AuditContext actor = new AuditContext(tenantId, UUID.randomUUID(), null, ActorType.TENANT_USER, null, "Agent");

        when(apiKeyScopeValidator.validateAndNormalize(request.scopes()))
                .thenReturn(List.of(Scopes.DOCUMENTS_READ));

        assertThatThrownBy(() -> apiKeyService.create(request, tenantId, actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresAt debe ser una fecha futura");
    }

    @Test
    void createRejectsSecondKeyWithManageScope() {
        UUID tenantId = UUID.randomUUID();
        CreateApiKeyRequest request = new CreateApiKeyRequest(
                "Second Admin",
                ApiKeyType.SERVICE,
                List.of(Scopes.API_KEYS_MANAGE),
                null);
        AuditContext actor = new AuditContext(tenantId, null, UUID.randomUUID(), ActorType.API_KEY, null, "Agent");

        when(apiKeyScopeValidator.validateAndNormalize(request.scopes()))
                .thenReturn(List.of(Scopes.API_KEYS_MANAGE));
        when(apiKeyRepository.countActiveWithScope(tenantId, Scopes.API_KEYS_MANAGE)).thenReturn(1L);

        assertThatThrownBy(() -> apiKeyService.create(request, tenantId, actor))
                .isInstanceOf(RecursoDuplicadoException.class)
                .hasMessageContaining("Ya existe una key de administración activa");
    }

    @Test
    void revokeRejectsLastManageKey() {
        UUID tenantId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        ApiKey key = new ApiKey();
        key.setId(keyId);
        key.setTenantId(tenantId);
        key.setName("Root Key");
        key.setScopes(List.of(Scopes.API_KEYS_MANAGE));
        key.setKeyHash("somehash");

        when(apiKeyRepository.findByIdAndTenantId(keyId, tenantId)).thenReturn(Optional.of(key));
        when(apiKeyRepository.countActiveWithScope(tenantId, Scopes.API_KEYS_MANAGE)).thenReturn(1L);

        AuditContext actor = new AuditContext(tenantId, null, keyId, ActorType.API_KEY, null, "Agent");

        assertThatThrownBy(() -> apiKeyService.revoke(tenantId, keyId, actor))
                .isInstanceOf(EstadoTransicionInvalidoException.class)
                .hasMessageContaining("No se puede revocar la última key de administración activa");
    }

    @Test
    void revokeEvictsCacheAndPublishesAuditEvent() {
        UUID tenantId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        ApiKey key = new ApiKey();
        key.setId(keyId);
        key.setTenantId(tenantId);
        key.setName("Standard Key");
        key.setScopes(List.of(Scopes.DOCUMENTS_READ));
        key.setKeyHash("hash123");

        when(apiKeyRepository.findByIdAndTenantId(keyId, tenantId)).thenReturn(Optional.of(key));

        AuditContext actor = new AuditContext(tenantId, null, UUID.randomUUID(), ActorType.API_KEY, null, "Agent");

        apiKeyService.revoke(tenantId, keyId, actor);

        assertThat(key.getRevokedAt()).isNotNull();
        verify(apiKeyRepository).save(key);
        verify(apiKeyCacheEvictor).evictByHash("hash123");
        verify(auditEventPublisher).publish(any(AuditEvent.class));
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
