package dev.achiri.multivault.infrastructure.security.jwt.model;

import java.util.UUID;

public record ValidatedJwt(
        UUID tenantId,
        String subject,
        String email,
        String displayName
) {
}
