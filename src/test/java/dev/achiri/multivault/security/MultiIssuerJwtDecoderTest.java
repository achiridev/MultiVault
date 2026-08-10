package dev.achiri.multivault.security;

import dev.achiri.multivault.infrastructure.security.codec.JwtJackson3Deserializer;
import dev.achiri.multivault.infrastructure.security.codec.JwtJackson3Serializer;
import dev.achiri.multivault.infrastructure.security.jwt.MultiIssuerJwtDecoder;
import dev.achiri.multivault.infrastructure.security.jwt.exception.InvalidJwtException;
import dev.achiri.multivault.infrastructure.security.jwt.jwks.JwkEntry;
import dev.achiri.multivault.infrastructure.security.jwt.jwks.JwksProvider;
import dev.achiri.multivault.infrastructure.security.jwt.model.ValidatedJwt;
import dev.achiri.multivault.tenant.model.TenantIdentityProvider;
import dev.achiri.multivault.tenant.repository.TenantIdentityProviderRepository;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiIssuerJwtDecoderTest {

    private static final String ISSUER = "https://idp.test";
    private static final String AUDIENCE = "https://api.test";
    private static final String JWKS_URI = "https://idp.test/jwks";
    private static final String SUBJECT = "user-1";
    private static final String KEY_ID = "test-key";

    private static final KeyPair KEY_PAIR = rsaKeyPair();
    private static final KeyPair OTHER_KEY_PAIR = rsaKeyPair();

    @Mock
    private TenantIdentityProviderRepository identityProviderRepository;

    @Mock
    private JwksProvider jwksProvider;

    private MultiIssuerJwtDecoder decoder;
    private JwtJackson3Serializer serializer;
    private TenantIdentityProvider provider;

    @BeforeEach
    void setUp() {
        JsonMapper mapper = new JsonMapper();
        serializer = new JwtJackson3Serializer(mapper);
        decoder = new MultiIssuerJwtDecoder(
                identityProviderRepository, jwksProvider, mapper, new JwtJackson3Deserializer(mapper));
        provider = new TenantIdentityProvider();
        provider.setTenantId(UUID.randomUUID());
        provider.setIssuer(ISSUER);
        provider.setJwksUri(JWKS_URI);
        provider.setAudience(AUDIENCE);
        provider.setAllowedAlgorithms(List.of("RS256"));
        provider.setClockSkewSeconds(60);
        provider.setIsActive(true);
    }

    @Test
    void rejectsJwtWithUnknownIssuer() {
        String issuer = "https://other.test";
        String token = jwt(KEY_ID, KEY_PAIR, issuer);
        when(identityProviderRepository.findByIssuerAndIsActiveTrue(issuer)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> decoder.authenticate(token))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessageContaining("issuer no configurado");
    }

    @Test
    void rejectsJwtWhenJwksIsEmpty() {
        String token = jwt(KEY_ID, KEY_PAIR, ISSUER);
        when(identityProviderRepository.findByIssuerAndIsActiveTrue(ISSUER)).thenReturn(Optional.of(provider));
        when(jwksProvider.fetch(JWKS_URI)).thenReturn(List.of());

        assertThatThrownBy(() -> decoder.authenticate(token))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessageContaining("JWKS sin claves");
    }

    @Test
    void rejectsJwtWhenKeyIsNotRsa() {
        String token = jwt(KEY_ID, KEY_PAIR, ISSUER);
        when(identityProviderRepository.findByIssuerAndIsActiveTrue(ISSUER)).thenReturn(Optional.of(provider));
        when(jwksProvider.fetch(JWKS_URI)).thenReturn(List.of(new JwkEntry(KEY_ID, "EC", "ES256", "", "")));

        assertThatThrownBy(() -> decoder.authenticate(token))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessageContaining("kty no soportado");
    }

    @Test
    void fallsBackToFirstKeyWhenKidUnknown() {
        String token = jwt("unknown-kid", KEY_PAIR, ISSUER);
        when(identityProviderRepository.findByIssuerAndIsActiveTrue(ISSUER)).thenReturn(Optional.of(provider));
        when(jwksProvider.fetch(JWKS_URI)).thenReturn(jwkList(KEY_ID, KEY_PAIR));

        ValidatedJwt validated = decoder.authenticate(token);

        assertThat(validated.tenantId()).isEqualTo(provider.getTenantId());
        assertThat(validated.subject()).isEqualTo(SUBJECT);
        assertThat(validated.email()).isEqualTo(SUBJECT + "@test.com");
    }

    @Test
    void evictsCacheAndRetriesOnSignatureMismatch() {
        String token = jwt(KEY_ID, OTHER_KEY_PAIR, ISSUER);
        when(identityProviderRepository.findByIssuerAndIsActiveTrue(ISSUER)).thenReturn(Optional.of(provider));
        when(jwksProvider.fetch(JWKS_URI))
                .thenReturn(jwkList(KEY_ID, KEY_PAIR))
                .thenReturn(jwkList(KEY_ID, OTHER_KEY_PAIR));

        ValidatedJwt validated = decoder.authenticate(token);

        assertThat(validated.subject()).isEqualTo(SUBJECT);
        verify(jwksProvider).evict(JWKS_URI);
    }

    private String jwt(String kid, KeyPair keyPair, String issuer) {
        return Jwts.builder()
                .serializeToJsonWith(serializer)
                .header().keyId(kid).and()
                .issuer(issuer)
                .subject(SUBJECT)
                .setAudience(AUDIENCE)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .claim("email", SUBJECT + "@test.com")
                .claim("name", "User One")
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private List<JwkEntry> jwkList(String kid, KeyPair keyPair) {
        RSAPublicKey rsa = (RSAPublicKey) keyPair.getPublic();
        return List.of(new JwkEntry(
                kid, "RSA", "RS256", base64Url(rsa.getModulus()), base64Url(rsa.getPublicExponent())));
    }

    private String base64Url(BigInteger value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray());
    }

    private static KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
