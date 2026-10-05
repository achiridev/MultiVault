package dev.achiri.multivault.tenant.provisioning;

import java.util.UUID;

public record ProvisioningRequestedEvent(
        UUID tenantId,
        String schemaName,
        UUID planId
) {
}