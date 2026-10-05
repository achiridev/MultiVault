package dev.achiri.multivault.infrastructure.ratelimit.redis;

import dev.achiri.multivault.infrastructure.ratelimit.config.RateLimitProperties;
import dev.achiri.multivault.infrastructure.ratelimit.model.BucketSpec;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitDecision;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;
import dev.achiri.multivault.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRateLimiterIntegrationTest extends BaseIntegrationTest {

    private static final BucketSpec THREE_PER_HOUR = new BucketSpec(3, 3, Duration.ofHours(1));

    @Autowired
    private RedisRateLimiter rateLimiter;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private RateLimitProperties properties;

    @BeforeEach
    void flushLimiterKeys() {
        Set<String> keys = redis.keys("mv:rl:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void allowsUpToCapacityThenRejects() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(consume("ip-1", THREE_PER_HOUR).allowed())
                    .as("intento %d", attempt).isTrue();
        }

        RateLimitDecision rejected = consume("ip-1", THREE_PER_HOUR);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.limit()).isEqualTo(3);
        assertThat(rejected.ruleId()).isEqualTo("onboarding");
        assertThat(rejected.scope()).isEqualTo(RateLimitScope.IP);
        assertThat(rejected.retryAfter()).isPositive();
    }

    @Test
    void reportsRemainingTokensWhileAllowing() {
        assertThat(consume("ip-2", THREE_PER_HOUR).remaining()).isEqualTo(2);
        assertThat(consume("ip-2", THREE_PER_HOUR).remaining()).isEqualTo(1);
        assertThat(consume("ip-2", THREE_PER_HOUR).remaining()).isZero();
    }

    @Test
    void isolatesBucketsPerClient() {
        consume("ip-3", THREE_PER_HOUR);
        consume("ip-3", THREE_PER_HOUR);
        consume("ip-3", THREE_PER_HOUR);

        assertThat(consume("ip-4", THREE_PER_HOUR).allowed()).isTrue();
    }

    @Test
    void isolatesBucketsPerScope() {
        consume("shared-key", THREE_PER_HOUR);
        consume("shared-key", THREE_PER_HOUR);
        consume("shared-key", THREE_PER_HOUR);

        RateLimitDecision other = rateLimiter.consume("shared-key", RateLimitScope.GLOBAL, "onboarding", THREE_PER_HOUR);

        assertThat(other.allowed()).isTrue();
        assertThat(other.remaining()).isEqualTo(2);
    }

    @Test
    void storesOnlyHashedKeysInRedis() {
        consume("198.51.100.7", THREE_PER_HOUR);

        Set<String> keys = redis.keys("mv:rl:*");

        assertThat(keys).isNotNull().hasSize(1);
        assertThat(keys).allSatisfy(key -> assertThat(key).doesNotContain("198.51.100.7"));
    }

    @Test
    void expiresKeysSoRotatingClientsCannotFillRedis() {
        consume("ip-5", new BucketSpec(3, 3, Duration.ofSeconds(30)));

        String key = redis.keys("mv:rl:*").stream().findFirst().orElseThrow();
        Long ttl = redis.getExpire(key);

        assertThat(ttl).isNotNull().isPositive();
        assertThat(ttl).isLessThanOrEqualTo(properties.longestRefillPeriod().plusSeconds(120).toSeconds());
    }

    @Test
    void refillsTokensAfterTheWindowElapses() throws InterruptedException {
        consume("ip-6", new BucketSpec(1, 1, Duration.ofSeconds(1)));
        assertThat(consume("ip-6", new BucketSpec(1, 1, Duration.ofSeconds(1))).allowed()).isFalse();

        Thread.sleep(1200);

        assertThat(consume("ip-6", new BucketSpec(1, 1, Duration.ofSeconds(1))).allowed()).isTrue();
    }

    @Test
    void persistsBucketStateInRedisSoAnyInstanceSeesIt() {
        BucketSpec spec = new BucketSpec(2, 2, Duration.ofHours(1));
        assertThat(consume("ip-7", spec).allowed()).isTrue();

        RedisRateLimiter otherReplica = new RedisRateLimiter(rateLimiter.proxyManager(), new RateLimitKeyHasher(
                "mv:rl:", "test-salt"));

        assertThat(otherReplica.consume("ip-7", RateLimitScope.IP, "onboarding", spec).allowed()).isTrue();
        assertThat(otherReplica.consume("ip-7", RateLimitScope.IP, "onboarding", spec).allowed()).isFalse();
    }

    @Test
    void hashesKeysDeterministicallyAndCaseSensitively() {
        RateLimitKeyHasher hasher = new RateLimitKeyHasher("mv:rl:", "pepper");

        assertThat(hasher.hash("rule", RateLimitScope.IP, "a")).isEqualTo(hasher.hash("rule", RateLimitScope.IP, "a"));
        assertThat(hasher.hash("rule", RateLimitScope.IP, "a")).isNotEqualTo(hasher.hash("rule", RateLimitScope.IP, "A"));
        assertThat(hasher.hash("other", RateLimitScope.IP, "a")).isNotEqualTo(hasher.hash("rule", RateLimitScope.IP, "a"));
        assertThat(new RateLimitKeyHasher("mv:rl:", "other-pepper").hash("rule", RateLimitScope.IP, "a"))
                .isNotEqualTo(hasher.hash("rule", RateLimitScope.IP, "a"));
    }

    @Test
    void includesRuleAndScopeInTheRedisKey() {
        RateLimitKeyHasher hasher = new RateLimitKeyHasher("mv:rl:", "pepper");

        assertThat(hasher.hash("onboarding", RateLimitScope.IP, "1.2.3.4"))
                .startsWith("mv:rl:onboarding:ip:")
                .doesNotContain("1.2.3.4");
    }

    private RateLimitDecision consume(String key, BucketSpec spec) {
        return rateLimiter.consume(key, RateLimitScope.IP, "onboarding", spec);
    }
}