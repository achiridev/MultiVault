package dev.achiri.multivault.tenant.service;

import dev.achiri.multivault.apikey.service.ApiKeyResult;
import dev.achiri.multivault.tenant.model.Tenant;

public record ActivationResult(
        Tenant tenant,
        ApiKeyResult apiKey
) {
}
