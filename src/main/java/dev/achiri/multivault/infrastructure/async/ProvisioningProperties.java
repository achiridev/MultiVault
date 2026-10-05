package dev.achiri.multivault.infrastructure.async;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "multivault.provisioning")
public record ProvisioningProperties(
        boolean enabled,
        String streamKey,
        String deadLetterKey,
        String consumerGroup,
        String consumerName,
        int maxAttempts,
        Duration visibilityTimeout,
        Duration reconcileInterval,
        Duration staleAfter,
        int maxStreamLength
) {

    private static final boolean ENABLED_BY_DEFAULT = true;
    private static final String DEFAULT_STREAM_KEY = "mv:provisioning:jobs";
    private static final String DEFAULT_DLQ_KEY = "mv:provisioning:jobs:dlq";
    private static final String DEFAULT_CONSUMER_GROUP = "mv-provisioners";
    private static final String DEFAULT_CONSUMER_NAME = "worker";
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_VISIBILITY_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration DEFAULT_RECONCILE_INTERVAL = Duration.ofMinutes(2);
    private static final Duration DEFAULT_STALE_AFTER = Duration.ofMinutes(10);
    private static final int DEFAULT_MAX_STREAM_LENGTH = 10_000;

    public ProvisioningProperties {
        streamKey = blankTo(streamKey, DEFAULT_STREAM_KEY);
        deadLetterKey = blankTo(deadLetterKey, DEFAULT_DLQ_KEY);
        consumerGroup = blankTo(consumerGroup, DEFAULT_CONSUMER_GROUP);
        consumerName = blankTo(consumerName, DEFAULT_CONSUMER_NAME);
        maxAttempts = maxAttempts <= 0 ? DEFAULT_MAX_ATTEMPTS : maxAttempts;
        visibilityTimeout = positiveOr(visibilityTimeout, DEFAULT_VISIBILITY_TIMEOUT);
        reconcileInterval = positiveOr(reconcileInterval, DEFAULT_RECONCILE_INTERVAL);
        staleAfter = positiveOr(staleAfter, DEFAULT_STALE_AFTER);
        maxStreamLength = maxStreamLength <= 0 ? DEFAULT_MAX_STREAM_LENGTH : maxStreamLength;
    }

    private static String blankTo(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : candidate;
    }

    private static Duration positiveOr(Duration candidate, Duration fallback) {
        return candidate == null || candidate.isNegative() || candidate.isZero() ? fallback : candidate;
    }
}