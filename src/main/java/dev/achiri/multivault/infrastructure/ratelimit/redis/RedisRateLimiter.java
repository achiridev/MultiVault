package dev.achiri.multivault.infrastructure.ratelimit.redis;

import dev.achiri.multivault.infrastructure.ratelimit.model.BucketSpec;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitDecision;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;
import dev.achiri.multivault.infrastructure.ratelimit.spi.RateLimiter;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;

import java.time.Duration;

public class RedisRateLimiter implements RateLimiter {

    private final ProxyManager<String> proxyManager;
    private final RateLimitKeyHasher keyHasher;

    public RedisRateLimiter(ProxyManager<String> proxyManager, RateLimitKeyHasher keyHasher) {
        this.proxyManager = proxyManager;
        this.keyHasher = keyHasher;
    }

    ProxyManager<String> proxyManager() {
        return proxyManager;
    }

    @Override
    public RateLimitDecision consume(String key, RateLimitScope scope, String ruleId, BucketSpec spec) {
        BucketProxy bucket = proxyManager.builder().build(keyHasher.hash(ruleId, scope, key), configuration(spec));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return RateLimitDecision.allowed(ruleId, scope, spec.capacity(), probe.getRemainingTokens());
        }
        return RateLimitDecision.rejected(ruleId, scope, spec.capacity(), retryAfter(probe, spec));
    }

    private BucketConfiguration configuration(BucketSpec spec) {
        return BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(spec.capacity())
                        .refillGreedy(spec.refillTokens(), spec.refillPeriod()))
                .build();
    }

    private Duration retryAfter(ConsumptionProbe probe, BucketSpec spec) {
        return Duration.ofNanos(probe.getNanosToWaitForRefill());
    }
}