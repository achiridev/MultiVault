package dev.achiri.multivault.security;

import dev.achiri.multivault.apikey.model.ApiKey;
import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.apikey.repository.ApiKeyRepository;
import dev.achiri.multivault.apikey.service.ApiKeyHasher;
import dev.achiri.multivault.support.BaseIntegrationTest;
import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.model.TenantStatus;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ApiKeyFilterIntegrationTest extends BaseIntegrationTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String PROTECTED_PATH =
            "/api/v1/tenants/identity-provider";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiKeyHasher apiKeyHasher;

    @Autowired
    private TenantRepository tenantRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setName("test tenant");
        tenant.setSchemaName("mv_test_" + UUID.randomUUID().toString().replace("-", ""));
        tenant.setStatus(TenantStatus.ACTIVE);
        tenantId = tenantRepository.saveAndFlush(tenant).getId();
    }

    @AfterEach
    void tearDown() {
        apiKeyRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void rejectsRequestWithoutBearerToken() throws Exception {
        mockMvc.perform(put(PROTECTED_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatesServiceKey() throws Exception {
        String rawKey = createKey(ApiKeyType.SERVICE, null, null);

        mockMvc.perform(put(PROTECTED_PATH)
                        .header("Authorization", "Bearer " + rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsServiceKeyWithoutRequiredScope() throws Exception {
        String rawKey = randomRawKey();
        ApiKey key = new ApiKey();
        key.setTenantId(tenantId);
        key.setName("limited key");
        key.setKeyPrefix(rawKey.substring(0, 12));
        key.setKeyHash(apiKeyHasher.sha256Hex(rawKey));
        key.setKeyType(ApiKeyType.SERVICE);
        key.setScopes(List.of("documents:read"));
        key.setCreatedByUserId(USER_ID);
        apiKeyRepository.saveAndFlush(key);

        mockMvc.perform(put(PROTECTED_PATH)
                        .header("Authorization", "Bearer " + rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsStandardKeyWithoutJwt() throws Exception {
        String rawKey = createKey(ApiKeyType.STANDARD, null, null);

        mockMvc.perform(put(PROTECTED_PATH)
                        .header("Authorization", "Bearer " + rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRevokedKey() throws Exception {
        String rawKey = createKey(ApiKeyType.SERVICE, Instant.now().minusSeconds(60), null);

        mockMvc.perform(put(PROTECTED_PATH)
                        .header("Authorization", "Bearer " + rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredKey() throws Exception {
        String rawKey = createKey(ApiKeyType.SERVICE, null, Instant.now().minusSeconds(60));

        mockMvc.perform(put(PROTECTED_PATH)
                        .header("Authorization", "Bearer " + rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnknownKey() throws Exception {
        mockMvc.perform(put(PROTECTED_PATH)
                        .header("Authorization", "Bearer " + randomRawKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatesServiceKeyViaXApiKeyHeader() throws Exception {
        String rawKey = createKey(ApiKeyType.SERVICE, null, null);

        mockMvc.perform(put(PROTECTED_PATH)
                        .header("X-API-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsUnknownKeyViaXApiKeyHeader() throws Exception {
        mockMvc.perform(put(PROTECTED_PATH)
                        .header("X-API-Key", randomRawKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsUnauthorizedErrorResponseBody() throws Exception {
        mockMvc.perform(put(PROTECTED_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.mensaje").value("Autenticación requerida"));
    }

    @Test
    void serviceKeyTakesPriorityOverJwt() throws Exception {
        String rawKey = createKey(ApiKeyType.SERVICE, null, null);

        mockMvc.perform(put(PROTECTED_PATH)
                        .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.payload")
                        .header("X-API-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk());
    }

    @Test
    void leavesJwtTokensToFutureJwtFilter() throws Exception {
        mockMvc.perform(put(PROTECTED_PATH)
                        .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.payload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    private String createKey(ApiKeyType type, Instant revokedAt, Instant expiresAt) {
        String rawKey = randomRawKey();
        ApiKey key = new ApiKey();
        key.setTenantId(tenantId);
        key.setName("test key");
        key.setKeyPrefix(rawKey.substring(0, 12));
        key.setKeyHash(apiKeyHasher.sha256Hex(rawKey));
        key.setKeyType(type);
        key.setScopes(List.of("tenant:settings:write"));
        key.setCreatedByUserId(USER_ID);
        key.setRevokedAt(revokedAt);
        key.setExpiresAt(expiresAt);
        apiKeyRepository.saveAndFlush(key);
        return rawKey;
    }

    private String randomRawKey() {
        String hexA = UUID.randomUUID().toString().replace("-", "");
        String hexB = UUID.randomUUID().toString().replace("-", "");
        return "mv_live_" + hexA + hexB.substring(0, 8);
    }

    private String validBody() {
        return """
                {
                  "issuer": "https://idp.acme.com",
                  "jwksUri": "https://idp.acme.com/.well-known/jwks.json",
                  "audience": "https://api.acme.com",
                  "allowedAlgorithms": ["RS256"],
                  "clockSkewSeconds": 60
                }
                """;
    }
}
