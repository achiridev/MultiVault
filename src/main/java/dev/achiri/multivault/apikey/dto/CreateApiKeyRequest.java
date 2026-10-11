package dev.achiri.multivault.apikey.dto;

import dev.achiri.multivault.apikey.model.ApiKeyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record CreateApiKeyRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @NotNull
        ApiKeyType keyType,

        @NotEmpty
        List<String> scopes,

        Instant expiresAt
) {
}
