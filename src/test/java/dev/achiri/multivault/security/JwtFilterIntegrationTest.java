package dev.achiri.multivault.security;

import com.sun.net.httpserver.HttpServer;
import dev.achiri.multivault.apikey.model.ApiKey;
import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.apikey.repository.ApiKeyRepository;
import dev.achiri.multivault.apikey.service.ApiKeyHasher;
import dev.achiri.multivault.infrastructure.security.codec.JwtJackson3Serializer;
import dev.achiri.multivault.support.BaseIntegrationTest;
import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.model.TenantMember;
import dev.achiri.multivault.tenant.model.TenantIdentityProvider;
import dev.achiri.multivault.tenant.model.TenantStatus;
import dev.achiri.multivault.tenant.repository.TenantIdentityProviderRepository;
import dev.achiri.multivault.tenant.repository.TenantMemberRepository;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class JwtFilterIntegrationTest extends BaseIntegrationTest {

    private static final String ISSUER = "https://idp.test";
    private static final String AUDIENCE = "https://api.test";
    private static final String SUBJECT = "user-1";
    private static final String KEY_ID = "test-key";
    private static final UUID USER_ID = UUID.randomUUID();

    private static final KeyPair VALID_KEY_PAIR = rsaKeyPair();
    private static final KeyPair OTHER_KEY_PAIR = rsaKeyPair();
    private static final KeyPair ROTATED_KEY_PAIR = rsaKeyPair();
    private static final String ROTATED_KEY_ID = "rotated-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantIdentityProviderRepository identityProviderRepository;

    @Autowired
    private TenantMemberRepository tenantMemberRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiKeyHasher apiKeyHasher;

    @Autowired
    private JwtJackson3Serializer jwtJackson3Serializer;

    private UUID tenantId;
    private HttpServer jwksServer;
    private volatile boolean serveRotatedJwks;

    @BeforeEach
    void setUp() throws IOException {
        tenantId = createTenant();
        jwksServer = HttpServer.create(new InetSocketAddress(0), 0);
        jwksServer.createContext("/jwks", exchange -> {
            byte[] body = (serveRotatedJwks ? rotatedJwksJson() : jwksJson()).getBytes(UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        jwksServer.start();
        configureIdentityProvider(tenantId, List.of("RS256"));
    }

    @AfterEach
    void tearDown() {
        jwksServer.stop(0);
        tenantRepository.deleteAll();
    }

    @Test
    void rejectsValidJwtWithoutApiKey() throws Exception {
        String token = jwt(KEY_ID, VALID_KEY_PAIR, ISSUER, SUBJECT, AUDIENCE, Instant.now().plusSeconds(300));

        mockMvc.perform(put("/api/v1/tenants/identity-provider")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());

        assertThat(tenantMemberRepository.findByTenantIdAndSubject(tenantId, SUBJECT)).isEmpty();
    }

    @Test
    void rejectsJwtWithUnknownIssuer() throws Exception {
        String token = jwt(KEY_ID, VALID_KEY_PAIR, "https://unknown.test", SUBJECT, AUDIENCE, Instant.now().plusSeconds(300));

        mockMvc.perform(put("/api/v1/tenants/identity-provider")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsJwtWithInvalidSignature() throws Exception {
        String token = jwt(KEY_ID, OTHER_KEY_PAIR, ISSUER, SUBJECT, AUDIENCE, Instant.now().plusSeconds(300));

        mockMvc.perform(put("/api/v1/tenants/identity-provider")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredJwt() throws Exception {
        String token = jwt(KEY_ID, VALID_KEY_PAIR, ISSUER, SUBJECT, AUDIENCE, Instant.now().minusSeconds(60));

        mockMvc.perform(put("/api/v1/tenants/identity-provider")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsJwtWithWrongAudience() throws Exception {
        String token = jwt(KEY_ID, VALID_KEY_PAIR, ISSUER, SUBJECT, "https://wrong.test", Instant.now().plusSeconds(300));

        mockMvc.perform(put("/api/v1/tenants/identity-provider")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsJwtWithDisallowedAlgorithm() throws Exception {
        configureIdentityProvider(tenantId, List.of("RS512"));
        String token = jwt(KEY_ID, VALID_KEY_PAIR, ISSUER, SUBJECT, AUDIENCE, Instant.now().plusSeconds(300));

        mockMvc.perform(put("/api/v1/tenants/identity-provider")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void combinesStandardKeyAndJwtFromSameTenant() throws Exception {
        String rawKey = createStandardKey(tenantId);
        String token = jwt(KEY_ID, VALID_KEY_PAIR, ISSUER, SUBJECT, AUDIENCE, Instant.now().plusSeconds(300));

        mockMvc.perform(put("/api/v1/tenants/identity-provider")
                        .header("Authorization", "Bearer " + token)
                        .header("X-API-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsStandardKeyAndJwtFromDifferentTenants() throws Exception {
        UUID otherTenantId = createTenant();
        String rawKey = createStandardKey(otherTenantId);
        String token = jwt(KEY_ID, VALID_KEY_PAIR, ISSUER, SUBJECT, AUDIENCE, Instant.now().plusSeconds(300));

        mockMvc.perform(put("/api/v1/tenants/identity-provider")
                        .header("Authorization", "Bearer " + token)
                        .header("X-API-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMalformedJwt() throws Exception {
        mockMvc.perform(put("/api/v1/tenants/identity-provider")
                        .header("Authorization", "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsJwtWithoutIssuerClaim() throws Exception {
        String token = Jwts.builder()
                .serializeToJsonWith(jwtJackson3Serializer)
                .header().keyId(KEY_ID).and()
                .subject(SUBJECT)
                .setAudience(AUDIENCE)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(VALID_KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
                .compact();

        mockMvc.perform(put("/api/v1/tenants/identity-provider")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsUnauthorizedErrorResponseBody() throws Exception {
        mockMvc.perform(put("/api/v1/tenants/identity-provider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.mensaje").value("Autenticación requerida"));
    }

    @Test
    void handlesJwksKeyRotation() throws Exception {
        String rawKey = createStandardKey(tenantId);
        String tokenA = jwt(KEY_ID, VALID_KEY_PAIR, ISSUER, SUBJECT, AUDIENCE, Instant.now().plusSeconds(300));

        mockMvc.perform(put("/api/v1/tenants/identity-provider")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("X-API-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());

        serveRotatedJwks = true;
        String tokenB = jwt(ROTATED_KEY_ID, ROTATED_KEY_PAIR, ISSUER, SUBJECT, AUDIENCE, Instant.now().plusSeconds(300));

        mockMvc.perform(put("/api/v1/tenants/identity-provider")
                        .header("Authorization", "Bearer " + tokenB)
                        .header("X-API-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatesExistingMemberOnReLogin() throws Exception {
        String rawKey = createStandardKey(tenantId);
        String firstToken = jwt(KEY_ID, VALID_KEY_PAIR, ISSUER, SUBJECT, AUDIENCE, Instant.now().plusSeconds(300));

        mockMvc.perform(put("/api/v1/tenants/identity-provider")
                        .header("Authorization", "Bearer " + firstToken)
                        .header("X-API-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());

        TenantMember firstLogin = tenantMemberRepository.findByTenantIdAndSubject(tenantId, SUBJECT).orElseThrow();

        String secondToken = jwt(KEY_ID, VALID_KEY_PAIR, ISSUER, SUBJECT, AUDIENCE, Instant.now().plusSeconds(300),
                "updated@test.com", "User Updated");

        mockMvc.perform(put("/api/v1/tenants/identity-provider")
                        .header("Authorization", "Bearer " + secondToken)
                        .header("X-API-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());

        TenantMember updated = tenantMemberRepository.findByTenantIdAndSubject(tenantId, SUBJECT).orElseThrow();
        assertThat(updated.getId()).isEqualTo(firstLogin.getId());
        assertThat(updated.getEmail()).isEqualTo("updated@test.com");
        assertThat(updated.getDisplayName()).isEqualTo("User Updated");
        assertThat(updated.getFirstSeenAt()).isEqualTo(firstLogin.getFirstSeenAt());
        assertThat(updated.getLastSeenAt()).isAfterOrEqualTo(firstLogin.getLastSeenAt());
        assertThat(tenantMemberRepository.findAll().stream()
                .filter(member -> member.getTenantId().equals(tenantId))
                .toList()).hasSize(1);
    }

    private UUID createTenant() {
        Tenant tenant = new Tenant();
        tenant.setName("test tenant");
        tenant.setSchemaName("mv_test_" + UUID.randomUUID().toString().replace("-", ""));
        tenant.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.saveAndFlush(tenant).getId();
    }

    private void configureIdentityProvider(UUID tenantId, List<String> allowedAlgorithms) {
        TenantIdentityProvider provider = new TenantIdentityProvider();
        provider.setTenantId(tenantId);
        provider.setIssuer(ISSUER);
        provider.setJwksUri("http://localhost:" + jwksServer.getAddress().getPort() + "/jwks");
        provider.setAudience(AUDIENCE);
        provider.setAllowedAlgorithms(allowedAlgorithms);
        provider.setClockSkewSeconds(60);
        provider.setIsActive(true);
        identityProviderRepository.saveAndFlush(provider);
    }

    private String createStandardKey(UUID tenantId) {
        String rawKey = "mv_live_" + UUID.randomUUID().toString().replace("-", "");
        ApiKey key = new ApiKey();
        key.setTenantId(tenantId);
        key.setName("test key");
        key.setKeyPrefix(rawKey.substring(0, 12));
        key.setKeyHash(apiKeyHasher.sha256Hex(rawKey));
        key.setKeyType(ApiKeyType.STANDARD);
        key.setScopes(List.of("tenant:settings:write"));
        key.setCreatedByUserId(USER_ID);
        apiKeyRepository.saveAndFlush(key);
        return rawKey;
    }

    private String jwt(String kid, KeyPair keyPair, String issuer, String subject, String audience,
                       Instant expiresAt) {
        return jwt(kid, keyPair, issuer, subject, audience, expiresAt, subject + "@test.com", "User One");
    }

    private String jwt(String kid, KeyPair keyPair, String issuer, String subject, String audience,
                       Instant expiresAt, String email, String displayName) {
        return Jwts.builder()
                .serializeToJsonWith(jwtJackson3Serializer)
                .header().keyId(kid).and()
                .issuer(issuer)
                .subject(subject)
                .setAudience(audience)
                .expiration(Date.from(expiresAt))
                .claim("email", email)
                .claim("name", displayName)
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String jwksJson() {
        RSAPublicKey rsa = (RSAPublicKey) VALID_KEY_PAIR.getPublic();
        return """
                {"keys":[{"kid":"test-key","kty":"RSA","alg":"RS256","n":"%s","e":"%s"}]}
                """.formatted(base64Url(rsa.getModulus()), base64Url(rsa.getPublicExponent()));
    }

    private String rotatedJwksJson() {
        RSAPublicKey rsa = (RSAPublicKey) ROTATED_KEY_PAIR.getPublic();
        return """
                {"keys":[{"kid":"rotated-key","kty":"RSA","alg":"RS256","n":"%s","e":"%s"}]}
                """.formatted(base64Url(rsa.getModulus()), base64Url(rsa.getPublicExponent()));
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

    private String validBody() {
        return """
                {
                  "issuer": "%s",
                  "jwksUri": "http://localhost:%d/jwks",
                  "audience": "%s",
                  "allowedAlgorithms": ["RS256"],
                  "clockSkewSeconds": 60
                }
                """.formatted(ISSUER, jwksServer.getAddress().getPort(), AUDIENCE);
    }
}
