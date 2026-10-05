package dev.achiri.multivault.infrastructure.ratelimit.model;

import java.time.Duration;

public record RateLimitDecision(
        boolean allowed,
        String ruleId,
        RateLimitScope scope,
        long limit,
        long remaining,
        Duration retryAfter
) {

    private static final RateLimitDecision ALLOWED = new RateLimitDecision(true, null, null, 0, 0, Duration.ZERO);

    public static RateLimitDecision allowed(String ruleId, RateLimitScope scope, long limit, long remaining) {
        return new RateLimitDecision(true, ruleId, scope, limit, remaining, Duration.ZERO);
    }

    public static RateLimitDecision rejected(String ruleId, RateLimitScope scope, long limit, Duration retryAfter) {
        return new RateLimitDecision(false, ruleId, scope, limit, 0, retryAfter);
    }

    public static RateLimitDecision unlimited() {
        return ALLOWED;
    }

    public long retryAfterSeconds() {
        return Math.max(1, retryAfter.toSeconds());
    }
}