package dev.achiri.multivault.infrastructure.idempotency.model;

import java.time.Instant;

public record IdempotencyRecord(
        IdempotencyState state,
        String fingerprint,
        StoredResponse response,
        Instant createdAt
) {

    public static IdempotencyRecord inProgress(String fingerprint, Instant createdAt) {
        return new IdempotencyRecord(IdempotencyState.IN_PROGRESS, fingerprint, null, createdAt);
    }

    public static IdempotencyRecord completed(String fingerprint, StoredResponse response, Instant createdAt) {
        return new IdempotencyRecord(IdempotencyState.COMPLETED, fingerprint, response, createdAt);
    }

    public boolean matchesFingerprint(String candidate) {
        return fingerprint.equals(candidate);
    }
}