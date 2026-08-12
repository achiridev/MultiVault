package dev.achiri.multivault.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateDocumentRequest(
        @NotBlank
        @Size(max = 500)
        String name,

        @NotBlank
        @Size(max = 150)
        String mimeType,

        @NotNull
        @Positive
        Long sizeBytes,

        @NotBlank
        @Size(max = 128)
        String checksum,

        UUID folderId,

        UUID ownerUserId
) {
}
