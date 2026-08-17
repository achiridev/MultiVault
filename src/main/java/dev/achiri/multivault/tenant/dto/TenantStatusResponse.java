package dev.achiri.multivault.tenant.dto;

import dev.achiri.multivault.tenant.model.TenantStatus;

import java.time.Instant;
import java.util.UUID;

public record TenantStatusResponse(
        UUID id,
        String name,
        TenantStatus previousStatus,
        TenantStatus currentStatus,
        Instant suspendedAt,
        String suspendedReason
) {
}
