package dev.achiri.multivault.infrastructure.ratelimit.resilience;

import dev.achiri.multivault.infrastructure.ratelimit.model.BucketSpec;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitDecision;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LocalRateLimiterTest {

    private static final BucketSpec THREE_PER_HOUR = new BucketSpec(3, 3, Duration.ofHours(1));

    @Test
    void allowsUpToCapacityThenRejects() {
        LocalRateLimiter limiter = new LocalRateLimiter();

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(consume(limiter, "ip-1").allowed()).as("intento %d", attempt).isTrue();
        }
        assertThat(consume(limiter, "ip-1").allowed()).isFalse();
    }

    @Test
    void isolatesBucketsPerClient() {
        LocalRateLimiter limiter = new LocalRateLimiter();
        consume(limiter, "ip-1");
        consume(limiter, "ip-1");
        consume(limiter, "ip-1");

        assertThat(consume(limiter, "ip-2").allowed()).isTrue();
    }

    @Test
    void isolatesBucketsPerRule() {
        LocalRateLimiter limiter = new LocalRateLimiter();
        limiter.consume("shared", RateLimitScope.IP, "onboarding", THREE_PER_HOUR);

        RateLimitDecision other = limiter.consume("shared", RateLimitScope.IP, "api-default", THREE_PER_HOUR);

        assertThat(other.allowed()).isTrue();
    }

    @Test
    void refillsTokensOnceTheWindowElapses() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LocalRateLimiter limiter = new LocalRateLimiter(clock);
        BucketSpec perMinute = new BucketSpec(1, 1, Duration.ofMinutes(1));
        limiter.consume("ip-1", RateLimitScope.IP, "onboarding", perMinute);

        clock.advance(Duration.ofSeconds(30));
        assertThat(limiter.consume("ip-1", RateLimitScope.IP, "onboarding", perMinute).allowed()).isFalse();

        clock.advance(Duration.ofSeconds(31));
        assertThat(limiter.consume("ip-1", RateLimitScope.IP, "onboarding", perMinute).allowed()).isTrue();
    }

    @Test
    void reportsRetryAfterOnRejection() {
        LocalRateLimiter limiter = new LocalRateLimiter();
        consume(limiter, "ip-1");
        consume(limiter, "ip-1");
        consume(limiter, "ip-1");

        RateLimitDecision decision = consume(limiter, "ip-1");

        assertThat(decision.retryAfterSeconds()).isPositive();
        assertThat(decision.limit()).isEqualTo(3);
    }

    @Test
    void evictsIdleBucketsSoARotatingClientCannotGrowMemoryWithoutBound() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LocalRateLimiter limiter = new LocalRateLimiter(clock);
        BucketSpec spec = new BucketSpec(1, 1, Duration.ofMinutes(1));
        for (int client = 0; client < 50; client++) {
            limiter.consume("ip-" + client, RateLimitScope.IP, "onboarding", spec);
        }
        assertThat(limiter.trackedBuckets()).isEqualTo(50);

        clock.advance(Duration.ofHours(1));
        for (int request = 0; request < 64; request++) {
            limiter.consume("ip-nuevo", RateLimitScope.IP, "onboarding", spec);
        }

        assertThat(limiter.trackedBuckets()).isLessThan(50);
    }

    private static RateLimitDecision consume(LocalRateLimiter limiter, String key) {
        return limiter.consume(key, RateLimitScope.IP, "onboarding", THREE_PER_HOUR);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}