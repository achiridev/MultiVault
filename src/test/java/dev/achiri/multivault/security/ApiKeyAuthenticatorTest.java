package dev.achiri.multivault.security;

import dev.achiri.multivault.apikey.model.ApiKey;
import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.apikey.repository.ApiKeyRepository;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyAuthenticator;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticatorTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Test
    void mapsFoundKeyToIdentity() {
        UUID keyId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(3600);
        ApiKey key = new ApiKey();
        key.setId(keyId);
        key.setTenantId(tenantId);
        key.setName("service key");
        key.setKeyType(ApiKeyType.SERVICE);
        key.setScopes(List.of("documents:read"));
        key.setExpiresAt(expiresAt);
        when(apiKeyRepository.findByKeyHashAndRevokedAtIsNull("hash")).thenReturn(Optional.of(key));

        ApiKeyIdentity identity = new ApiKeyAuthenticator(apiKeyRepository).findValidByHash("hash");

        assertThat(identity.keyId()).isEqualTo(keyId);
        assertThat(identity.tenantId()).isEqualTo(tenantId);
        assertThat(identity.name()).isEqualTo("service key");
        assertThat(identity.keyType()).isEqualTo(ApiKeyType.SERVICE);
        assertThat(identity.scopes()).containsExactly("documents:read");
        assertThat(identity.expiresAtEpochSecond()).isEqualTo(expiresAt.getEpochSecond());
    }

    @Test
    void mapsKeyWithoutExpirationToZero() {
        ApiKey key = new ApiKey();
        key.setExpiresAt(null);
        when(apiKeyRepository.findByKeyHashAndRevokedAtIsNull("hash")).thenReturn(Optional.of(key));

        ApiKeyIdentity identity = new ApiKeyAuthenticator(apiKeyRepository).findValidByHash("hash");

        assertThat(identity.expiresAtEpochSecond()).isZero();
        assertThat(identity.isExpired()).isFalse();
    }

    @Test
    void returnsNullWhenKeyNotFound() {
        when(apiKeyRepository.findByKeyHashAndRevokedAtIsNull("hash")).thenReturn(Optional.empty());

        assertThat(new ApiKeyAuthenticator(apiKeyRepository).findValidByHash("hash")).isNull();
    }
}
