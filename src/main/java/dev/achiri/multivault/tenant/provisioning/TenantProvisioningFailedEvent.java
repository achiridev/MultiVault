package dev.achiri.multivault.tenant.provisioning;

import java.util.UUID;

public record TenantProvisioningFailedEvent(
        UUID tenantId,
        String schemaName,
        String reason
) {
}
