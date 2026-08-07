package dev.achiri.multivault.tenant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateTenantRequest(
        @NotBlank
        String name,

        @NotNull
        UUID planId,

        @NotNull
        @Valid
        TenantAdminDto admin,

        @Valid
        TenantIdentityProviderDto identityProvider
) {
    public record TenantAdminDto(
            @NotBlank
            String subject,

            @NotBlank
            @Email
            String email,

            @Size(max = 255)
            String displayName
    ) {
    }

    public record TenantIdentityProviderDto(
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
}
