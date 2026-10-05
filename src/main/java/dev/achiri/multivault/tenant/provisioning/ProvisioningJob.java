package dev.achiri.multivault.tenant.provisioning;

import java.time.Instant;
import java.util.UUID;

public record ProvisioningJob(
        UUID tenantId,
        String schemaName,
        UUID planId,
        int attempt,
        Instant enqueuedAt
) {

    public ProvisioningJob nextAttempt() {
        return new ProvisioningJob(tenantId, schemaName, planId, attempt + 1, Instant.now());
    }
}