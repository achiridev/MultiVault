package dev.achiri.multivault.infrastructure.idempotency.web;

import java.util.Optional;
import java.util.regex.Pattern;

public final class IdempotencyKeyValidator {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final int MAX_LENGTH = 64;

    private IdempotencyKeyValidator() {
    }

    public static Optional<String> invalidReason(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.of("Idempotency-Key es requerido");
        }
        String candidate = rawKey.trim();
        if (candidate.length() > MAX_LENGTH) {
            return Optional.of("Idempotency-Key excede " + MAX_LENGTH + " caracteres");
        }
        if (!UUID_PATTERN.matcher(candidate).matches()) {
            return Optional.of("Idempotency-Key debe ser un UUID");
        }
        return Optional.empty();
    }
}