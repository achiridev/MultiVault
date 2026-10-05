package dev.achiri.multivault.infrastructure.ratelimit.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPropertiesTest {

    @Test
    void bindsRulesWithScopesAndSpecs() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("multivault.ratelimit.enabled", "true")
                .withProperty("multivault.ratelimit.key-salt", "pepper")
                .withProperty("multivault.ratelimit.rules[0].id", "onboarding")
                .withProperty("multivault.ratelimit.rules[0].match[0]", "POST:/api/v1/tenants")
                .withProperty("multivault.ratelimit.rules[0].scopes[0]", "IP")
                .withProperty("multivault.ratelimit.rules[0].scopes[1]", "GLOBAL")
                .withProperty("multivault.ratelimit.rules[0].spec.capacity", "3")
                .withProperty("multivault.ratelimit.rules[0].spec.refill-tokens", "3")
                .withProperty("multivault.ratelimit.rules[0].spec.refill-period", "PT1H");

        RateLimitProperties properties = bind(environment);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.keySalt()).isEqualTo("pepper");
        assertThat(properties.keyPrefix()).isEqualTo("mv:rl:");
        assertThat(properties.failMode()).isEqualTo(RateLimitProperties.FailMode.CLOSED);
        assertThat(properties.rules()).hasSize(1);
        RateLimitProperties.Rule rule = properties.rules().getFirst();
        assertThat(rule.id()).isEqualTo("onboarding");
        assertThat(rule.match()).containsExactly("POST:/api/v1/tenants");
        assertThat(rule.spec().toBucketSpec().capacity()).isEqualTo(3);
        assertThat(properties.validationErrors()).isEmpty();
    }

    @Test
    void rejectsMissingKeySaltWhenEnabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("multivault.ratelimit.enabled", "true");

        assertThat(bind(environment).validationErrors())
                .singleElement().asString().contains("key-salt");
    }

    @Test
    void acceptsMissingKeySaltWhenDisabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("multivault.ratelimit.enabled", "false");

        assertThat(bind(environment).validationErrors()).isEmpty();
    }

    @Test
    void rejectsRuleWithoutSpec() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("multivault.ratelimit.key-salt", "pepper")
                .withProperty("multivault.ratelimit.rules[0].id", "onboarding")
                .withProperty("multivault.ratelimit.rules[0].match[0]", "POST:/api/v1/tenants");

        assertThat(bind(environment).validationErrors())
                .anyMatch(error -> error.contains("onboarding"));
    }

    @Test
    void reportsLongestRefillPeriodAcrossRules() {
        RateLimitProperties properties = new RateLimitProperties(true, "mv:rl:", "pepper",
                RateLimitProperties.FailMode.CLOSED,
                List.of(rule("a", Duration.ofMinutes(1)), rule("b", Duration.ofHours(2))));

        assertThat(properties.longestRefillPeriod()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void defaultsLongestRefillPeriodWhenNoRulesAreConfigured() {
        RateLimitProperties properties = new RateLimitProperties(true, "mv:rl:", "pepper",
                RateLimitProperties.FailMode.CLOSED, null);

        assertThat(properties.longestRefillPeriod()).isEqualTo(Duration.ofMinutes(1));
        assertThat(properties.rules()).isEmpty();
    }

    @Test
    void fallsBackToClosedFailModeAndDefaultPrefix() {
        RateLimitProperties properties = new RateLimitProperties(true, null, null, null, List.of());

        assertThat(properties.failMode()).isEqualTo(RateLimitProperties.FailMode.CLOSED);
        assertThat(properties.keyPrefix()).isEqualTo("mv:rl:");
    }

    private static RateLimitProperties.Rule rule(String id, Duration refillPeriod) {
        return new RateLimitProperties.Rule(id, List.of("/api/**"), List.of("IP"),
                new RateLimitProperties.Spec(10, 10, refillPeriod));
    }

    private static RateLimitProperties bind(MockEnvironment environment) {
        return Binder.get(environment).bind("multivault.ratelimit", RateLimitProperties.class)
                .orElseThrow(() -> new IllegalStateException("no se pudo enlazar multivault.ratelimit"));
    }
}