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
class TenantOnboardingAbuseTest extends BaseIntegrationTest {

    private static final String SCHEMA_PREFIX = "mv_";

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

    private String namePrefix;

    @BeforeEach
    void isolateRun() {
        Set<String> keys = redis.keys("mv:rl:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        namePrefix = "abuse" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase();
    }

    @Test
    void rejectsTheFourthOnboardingRequestFromTheSameIpWithoutCreatingATenant() throws Exception {
        long tenantsBefore = tenantRepository.count();

        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(post("/api/v1/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body()))
                    .andExpect(status().is2xxSuccessful());
        }

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("RateLimit-Limit", "3"));

        assertThat(tenantRepository.count()).isEqualTo(tenantsBefore + 3);
    }

    @Test
    void rejectedRequestsNeverReachSchemaCreation() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(post("/api/v1/tenants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body()));
        }

        int schemasAfterAllowed = schemasCreated();

        for (int attempt = 4; attempt <= 10; attempt++) {
            mockMvc.perform(post("/api/v1/tenants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body())).andExpect(status().isTooManyRequests());
        }

        assertThat(schemasCreated()).isEqualTo(schemasAfterAllowed);
    }

    @Test
    void abusiveClientCannotMakeTheApplicationCreateSchemasUnbounded() throws Exception {
        for (int attempt = 1; attempt <= 50; attempt++) {
            mockMvc.perform(post("/api/v1/tenants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body()));
        }

        assertThat(schemasCreated()).isEqualTo(3);
    }

    @Test
    void limitingIsPerClientIpSoRotatingAddressesGetTheirOwnBudget() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(post("/api/v1/tenants")
                            .remoteAddress("203.0.113." + attempt)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body()))
                    .andExpect(status().is2xxSuccessful());
        }

        assertThat(schemasCreated()).isEqualTo(3);
    }

    @Test
    void exhaustingTheOnboardingBucketDoesNotBlockOtherEndpointsFromTheSameIp() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(post("/api/v1/tenants")
                    .remoteAddress("203.0.113.90")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body()));
        }

        mockMvc.perform(post("/api/v1/tenants")
                        .remoteAddress("203.0.113.90")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/v1/tenants/status")
                        .remoteAddress("203.0.113.90")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private int schemasCreated() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name LIKE ?",
                Integer.class, SCHEMA_PREFIX + namePrefix + "%");
    }

    private String body() {
        return """
                {
                  "name": "%s %s",
                  "planId": "%s",
                  "admin": {
                    "subject": "sub_abuse",
                    "email": "admin@abuse.test"
                  },
                  "identityProvider": {
                    "issuer": "https://idp.abuse.test",
                    "jwksUri": "https://idp.abuse.test/v2/.well-known/jwks.json",
                    "audience": "https://api.abuse.test",
                    "allowedAlgorithms": ["RS256"]
                  }
                }
                """.formatted(namePrefix, UUID.randomUUID(), activePlanId());
    }

    private UUID activePlanId() {
        return planRepository.findAll().stream()
                .filter(Plan::getIsActive)
                .findFirst()
                .orElseThrow()
                .getId();
    }
}