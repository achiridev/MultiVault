package dev.achiri.multivault.tenant;

import dev.achiri.multivault.plan.model.Plan;
import dev.achiri.multivault.plan.repository.PlanRepository;
import dev.achiri.multivault.support.BaseIntegrationTest;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class IdempotencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tenantName;
    private String schemaName;

    @BeforeEach
    void prepare() {
        Set<String> limiterKeys = redis.keys("mv:rl:*");
        if (limiterKeys != null && !limiterKeys.isEmpty()) {
            redis.delete(limiterKeys);
        }
        Set<String> idempotencyKeys = redis.keys("mv:idem:*");
        if (idempotencyKeys != null && !idempotencyKeys.isEmpty()) {
            redis.delete(idempotencyKeys);
        }
        tenantName = "Idem " + UUID.randomUUID();
        schemaName = null;
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (schemaName != null) {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
    }

    @Test
    void replayingTheSameKeyReturnsTheStoredResponseWithoutCreatingASecondTenant() throws Exception {
        String key = UUID.randomUUID().toString();
        long tenantsBefore = tenantRepository.count();

        String first = mockMvc.perform(post("/api/v1/tenants")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andReturn().getResponse().getContentAsString();
        schemaName = schemaOf(first);

        String replay = mockMvc.perform(post("/api/v1/tenants")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat(replay).isEqualTo(first);
        assertThat(tenantRepository.count()).isEqualTo(tenantsBefore + 1);
    }

    @Test
    void replayDoesNotCreateASecondSchema() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = mockMvc.perform(post("/api/v1/tenants")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andReturn().getResponse().getContentAsString();
        schemaName = schemaOf(first);
        int schemasBefore = schemaCount(schemaName);

        mockMvc.perform(post("/api/v1/tenants")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body())).andExpect(status().is2xxSuccessful());

        assertThat(schemaCount(schemaName)).isEqualTo(schemasBefore);
    }

    @Test
    void reusingAKeyWithADifferentBodyIsRejected() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = mockMvc.perform(post("/api/v1/tenants")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andReturn().getResponse().getContentAsString();
        schemaName = schemaOf(first);

        mockMvc.perform(post("/api/v1/tenants")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithName("Other " + UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejectsMalformedIdempotencyKeys() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectedRequestsReleaseTheKeySoTheClientCanRetry() throws Exception {
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/tenants")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        String success = mockMvc.perform(post("/api/v1/tenants")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        schemaName = schemaOf(success);
    }

    @Test
    void requestsWithoutTheHeaderAreUnaffected() throws Exception {
        String response = mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        schemaName = schemaOf(response);
    }

    @Test
    void distinctKeysCreateDistinctTenants() throws Exception {
        long tenantsBefore = tenantRepository.count();

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/tenants")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyWithName("Idem " + UUID.randomUUID())))
                    .andExpect(status().is2xxSuccessful());
        }

        assertThat(tenantRepository.count()).isEqualTo(tenantsBefore + 2);
    }

    private int schemaCount(String schema) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class, schema);
    }

    private String schemaOf(String responseBody) {
        int index = responseBody.indexOf("\"schemaName\":\"");
        int start = index + "\"schemaName\":\"".length();
        return responseBody.substring(start, responseBody.indexOf('"', start));
    }

    private String body() {
        return bodyWithName(tenantName);
    }

    private String bodyWithName(String name) {
        return """
                {
                  "name": "%s",
                  "planId": "%s",
                  "admin": {
                    "subject": "sub_idem",
                    "email": "admin@idem.test"
                  },
                  "identityProvider": {
                    "issuer": "https://idp.idem.test",
                    "jwksUri": "https://idp.idem.test/v2/.well-known/jwks.json",
                    "audience": "https://api.idem.test",
                    "allowedAlgorithms": ["RS256"]
                  }
                }
                """.formatted(name, activePlanId());
    }

    private UUID activePlanId() {
        return planRepository.findAll().stream()
                .filter(Plan::getIsActive)
                .findFirst()
                .orElseThrow()
                .getId();
    }

    }