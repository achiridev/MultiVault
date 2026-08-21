package dev.achiri.multivault.document.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateDocumentRequest(
        @Size(max = 500)
        String name,

        @Size(max = 150)
        String mimeType,

        UUID folderId
) {
}
