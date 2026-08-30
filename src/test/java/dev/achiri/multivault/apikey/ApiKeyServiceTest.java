package dev.achiri.multivault.apikey;

import dev.achiri.multivault.apikey.model.ApiKey;
import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.apikey.repository.ApiKeyRepository;
import dev.achiri.multivault.apikey.service.ApiKeyHasher;
import dev.achiri.multivault.apikey.service.ApiKeyResult;
import dev.achiri.multivault.apikey.service.ApiKeyService;
import dev.achiri.multivault.infrastructure.security.apikey.Scopes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private ApiKeyHasher apiKeyHasher;

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

        assertThat(saved.getKeyHash()).isEqualTo(sha256Hex(result.rawKey()));
        assertThat(saved.getKeyHash()).isNotEqualTo(result.rawKey());
        assertThat(saved.getKeyType()).isEqualTo(ApiKeyType.SERVICE);
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getCreatedByUserId()).isEqualTo(memberId);
        assertThat(saved.getScopes()).containsExactly(Scopes.WILDCARD);
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
