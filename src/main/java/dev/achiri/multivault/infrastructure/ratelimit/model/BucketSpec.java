package dev.achiri.multivault.infrastructure.ratelimit.model;

import java.time.Duration;

public record BucketSpec(
        long capacity,
        long refillTokens,
        Duration refillPeriod
) {

    public BucketSpec {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity debe ser mayor que cero");
        }
        if (refillTokens <= 0) {
            throw new IllegalArgumentException("refillTokens debe ser mayor que cero");
        }
        if (refillPeriod == null || refillPeriod.isNegative() || refillPeriod.isZero()) {
            throw new IllegalArgumentException("refillPeriod debe ser positivo");
        }
    }
}