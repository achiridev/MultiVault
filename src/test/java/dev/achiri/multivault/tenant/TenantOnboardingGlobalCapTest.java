package dev.achiri.multivault.tenant;

import dev.achiri.multivault.plan.model.Plan;
import dev.achiri.multivault.plan.repository.PlanRepository;
import dev.achiri.multivault.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "multivault.ratelimit.rules[0].id=onboarding",
        "multivault.ratelimit.rules[0].match[0]=POST:/api/v1/tenants",
        "multivault.ratelimit.rules[0].scopes[0]=IP",
        "multivault.ratelimit.rules[0].spec.capacity=3",
        "multivault.ratelimit.rules[0].spec.refill-tokens=3",
        "multivault.ratelimit.rules[0].spec.refill-period=PT1H",
        "multivault.ratelimit.rules[1].id=onboarding-global",
        "multivault.ratelimit.rules[1].match[0]=POST:/api/v1/tenants",
        "multivault.ratelimit.rules[1].scopes[0]=GLOBAL",
        "multivault.ratelimit.rules[1].spec.capacity=3",
        "multivault.ratelimit.rules[1].spec.refill-tokens=3",
        "multivault.ratelimit.rules[1].spec.refill-period=PT1H"
})
class TenantOnboardingGlobalCapTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private PlanRepository planRepository;

    private String namePrefix;

    @BeforeEach
    void isolateRun() {
        Set<String> keys = redis.keys("mv:rl:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        namePrefix = "cap" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase();
    }

    @Test
    void theGlobalBucketRejectsFreshAddressesThatNeverExhaustedTheirOwn() throws Exception {
        for (int address = 1; address <= 3; address++) {
            mockMvc.perform(post("/api/v1/tenants")
                            .remoteAddress("203.0.113." + address)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body()))
                    .andExpect(status().is2xxSuccessful());
        }

        for (int address = 4; address <= 10; address++) {
            mockMvc.perform(post("/api/v1/tenants")
                            .remoteAddress("203.0.113." + address)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body()))
                    .andExpect(status().isTooManyRequests());
        }
    }

    @Test
    void anExhaustedGlobalBucketIsReportedAsTheSharedCeiling() throws Exception {
        for (int address = 1; address <= 3; address++) {
            mockMvc.perform(post("/api/v1/tenants")
                            .remoteAddress("203.0.113." + address)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body()))
                    .andExpect(status().is2xxSuccessful());
        }

        mockMvc.perform(post("/api/v1/tenants")
                        .remoteAddress("203.0.113.99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isTooManyRequests())
                .andExpect(result -> assertThat(result.getResponse().getHeader("RateLimit-Limit")).isEqualTo("3"));
    }

    private String body() {
        return """
                {
                  "name": "%s %s",
                  "planId": "%s",
                  "admin": {
                    "subject": "sub_cap",
                    "email": "admin@cap.test"
                  },
                  "identityProvider": {
                    "issuer": "https://idp.cap.test",
                    "jwksUri": "https://idp.cap.test/v2/.well-known/jwks.json",
                    "audience": "https://api.cap.test",
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