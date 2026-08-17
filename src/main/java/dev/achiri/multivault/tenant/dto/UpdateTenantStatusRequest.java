package dev.achiri.multivault.tenant.dto;

import dev.achiri.multivault.tenant.model.TenantStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTenantStatusRequest(
        @NotNull TenantStatus status,
        String reason
) {
}
