package dev.achiri.multivault.infrastructure.security.jwt.model;

import java.util.UUID;

public record TenantUserPrincipal(
        UUID memberId,
        UUID tenantId,
        String subject
) {
}
