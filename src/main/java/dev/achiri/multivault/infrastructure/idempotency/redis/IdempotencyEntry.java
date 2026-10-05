package dev.achiri.multivault.infrastructure.idempotency.redis;

public record IdempotencyEntry(
        String state,
        String fingerprint,
        long createdAtEpochSecond,
        Integer status,
        String contentType,
        byte[] body
) {
}