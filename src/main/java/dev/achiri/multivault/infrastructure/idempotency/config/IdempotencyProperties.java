package dev.achiri.multivault.infrastructure.idempotency.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "multivault.idempotency")
public record IdempotencyProperties(
        boolean enabled,
        String keyPrefix,
        Duration inProgressTtl,
        Duration completedTtl,
        int maxBodyBytes
) {

    private static final boolean ENABLED_BY_DEFAULT = true;
    private static final String DEFAULT_KEY_PREFIX = "mv:idem:";
    private static final Duration DEFAULT_IN_PROGRESS_TTL = Duration.ofMinutes(10);
    private static final Duration DEFAULT_COMPLETED_TTL = Duration.ofHours(24);
    private static final int DEFAULT_MAX_BODY_BYTES = 16_384;

    public IdempotencyProperties {
        keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? DEFAULT_KEY_PREFIX : keyPrefix;
        inProgressTtl = positiveOr(inProgressTtl, DEFAULT_IN_PROGRESS_TTL);
        completedTtl = positiveOr(completedTtl, DEFAULT_COMPLETED_TTL);
        maxBodyBytes = maxBodyBytes <= 0 ? DEFAULT_MAX_BODY_BYTES : maxBodyBytes;
    }

    private static Duration positiveOr(Duration candidate, Duration fallback) {
        return candidate == null || candidate.isNegative() || candidate.isZero() ? fallback : candidate;
    }
}