package dev.achiri.multivault.tenant.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UpdateTenantIdentityProviderRequest(
        @NotBlank
        String issuer,

        @NotBlank
        String jwksUri,

        @NotBlank
        String audience,

        List<String> allowedAlgorithms,

        Integer clockSkewSeconds
) {
}
