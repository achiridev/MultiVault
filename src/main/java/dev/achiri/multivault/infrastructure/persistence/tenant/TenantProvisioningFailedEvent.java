package dev.achiri.multivault.infrastructure.persistence.tenant;

import java.util.UUID;

public record TenantProvisioningFailedEvent(
        UUID tenantId,
        String schemaName,
        String reason
) {
}
