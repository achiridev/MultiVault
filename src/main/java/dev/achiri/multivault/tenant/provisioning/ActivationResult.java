package dev.achiri.multivault.tenant.provisioning;

import dev.achiri.multivault.apikey.service.ApiKeyResult;
import dev.achiri.multivault.tenant.model.Tenant;

public record ActivationResult(
        Tenant tenant,
        ApiKeyResult apiKey
) {
}
