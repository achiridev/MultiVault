package dev.achiri.multivault.infrastructure.ratelimit.resilience;

import dev.achiri.multivault.infrastructure.ratelimit.config.RateLimitProperties;
import dev.achiri.multivault.infrastructure.ratelimit.model.BucketSpec;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitDecision;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;
import dev.achiri.multivault.infrastructure.ratelimit.spi.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FailModeRateLimiterTest {

    private static final BucketSpec THREE_PER_HOUR = new BucketSpec(3, 3, Duration.ofHours(1));

    @Test
    void degradesToLocalLimiterWhenRedisIsUnavailableAndFailModeIsClosed() {
        LocalRateLimiter local = new LocalRateLimiter();
        FailModeRateLimiter limiter = new FailModeRateLimiter(
                failingLimiter(), local, RateLimitProperties.FailMode.CLOSED);

        assertThat(consumeRepeatedly(limiter, 3)).allMatch(RateLimitDecision::allowed);
        assertThat(limiter.consume("ip-1", RateLimitScope.IP, "onboarding", THREE_PER_HOUR).allowed()).isFalse();
    }

    @Test
    void capsTrafficEvenAfterManyRequestsWithRedisDown() {
        FailModeRateLimiter limiter = new FailModeRateLimiter(
                failingLimiter(), new LocalRateLimiter(), RateLimitProperties.FailMode.CLOSED);

        long allowed = 0;
        for (int attempt = 0; attempt < 50; attempt++) {
            if (limiter.consume("ip-1", RateLimitScope.IP, "onboarding", THREE_PER_HOUR).allowed()) {
                allowed++;
            }
        }

        assertThat(allowed).isEqualTo(THREE_PER_HOUR.capacity());
    }

    @Test
    void allowsTrafficWhenFailModeIsOpenAndRedisIsUnavailable() {
        FailModeRateLimiter limiter = new FailModeRateLimiter(
                failingLimiter(), new LocalRateLimiter(), RateLimitProperties.FailMode.OPEN);

        for (int attempt = 0; attempt < 10; attempt++) {
            assertThat(limiter.consume("ip-1", RateLimitScope.IP, "onboarding", THREE_PER_HOUR).allowed()).isTrue();
        }
    }

    @Test
    void delegatesToDistributedLimiterWhenRedisIsHealthy() {
        RecordingRateLimiter distributed = new RecordingRateLimiter();
        FailModeRateLimiter limiter = new FailModeRateLimiter(
                distributed, new LocalRateLimiter(), RateLimitProperties.FailMode.CLOSED);

        limiter.consume("ip-1", RateLimitScope.IP, "onboarding", THREE_PER_HOUR);

        assertThat(distributed.calls).isOne();
    }

    private static RateLimitDecision[] consumeRepeatedly(FailModeRateLimiter limiter, int times) {
        RateLimitDecision[] decisions = new RateLimitDecision[times];
        for (int index = 0; index < times; index++) {
            decisions[index] = limiter.consume("ip-1", RateLimitScope.IP, "onboarding", THREE_PER_HOUR);
        }
        return decisions;
    }

    private static RateLimiter failingLimiter() {
        return (key, scope, ruleId, spec) -> {
            throw new QueryTimeoutException("Redis no responde");
        };
    }

    private static final class RecordingRateLimiter implements RateLimiter {

        private int calls;

        @Override
        public RateLimitDecision consume(String key, RateLimitScope scope, String ruleId, BucketSpec spec) {
            calls++;
            return RateLimitDecision.allowed(ruleId, scope, spec.capacity(), spec.capacity() - 1);
        }
    }
}