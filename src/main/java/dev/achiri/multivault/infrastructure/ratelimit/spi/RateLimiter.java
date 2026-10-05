package dev.achiri.multivault.infrastructure.ratelimit.spi;

import dev.achiri.multivault.infrastructure.ratelimit.model.BucketSpec;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitDecision;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;

public interface RateLimiter {

    RateLimitDecision consume(String key, RateLimitScope scope, String ruleId, BucketSpec spec);
}