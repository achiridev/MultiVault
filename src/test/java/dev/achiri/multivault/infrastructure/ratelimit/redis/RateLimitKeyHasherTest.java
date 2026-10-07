package dev.achiri.multivault.infrastructure.ratelimit.redis;

import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitKeyHasherTest {

    private static final String PREFIX = "mv:rl:";

    @Test
    void derivesADistinctKeyPerRuleScopeAndValue() {
        RateLimitKeyHasher hasher = new RateLimitKeyHasher(PREFIX, "pepper");

        assertThat(hasher.hash("onboarding", RateLimitScope.IP, "203.0.113.5"))
                .isEqualTo(hasher.hash("onboarding", RateLimitScope.IP, "203.0.113.5"))
                .startsWith(PREFIX + "onboarding:ip:")
                .hasSize(PREFIX.length() + "onboarding:ip:".length() + 32);
        assertThat(hasher.hash("onboarding", RateLimitScope.IP, "203.0.113.5"))
                .isNotEqualTo(hasher.hash("onboarding", RateLimitScope.GLOBAL, "203.0.113.5"))
                .isNotEqualTo(hasher.hash("api-default", RateLimitScope.IP, "203.0.113.5"))
                .isNotEqualTo(hasher.hash("onboarding", RateLimitScope.IP, "203.0.113.6"));
    }

    @Test
    void saltChangesEveryDerivedKey() {
        assertThat(new RateLimitKeyHasher(PREFIX, "pepper").hash("onboarding", RateLimitScope.IP, "203.0.113.5"))
                .isNotEqualTo(new RateLimitKeyHasher(PREFIX, "other-pepper")
                        .hash("onboarding", RateLimitScope.IP, "203.0.113.5"));
    }

    @Test
    void constructionSucceedsWithoutSaltSoDisablingRateLimitingDoesNotBlockStartup() {
        RateLimitKeyHasher blank = new RateLimitKeyHasher(PREFIX, "");
        RateLimitKeyHasher absent = new RateLimitKeyHasher(PREFIX, null);

        assertThatThrownBy(() -> blank.hash("onboarding", RateLimitScope.IP, "203.0.113.5"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> absent.hash("onboarding", RateLimitScope.IP, "203.0.113.5"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hashingWithoutSaltFailsLoudly() {
        RateLimitKeyHasher hasher = new RateLimitKeyHasher(PREFIX, "  ");

        assertThatThrownBy(() -> hasher.hash("onboarding", RateLimitScope.IP, "203.0.113.5"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key-salt");
    }
}