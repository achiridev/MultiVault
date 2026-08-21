package dev.achiri.multivault.document.dto;

import jakarta.validation.constraints.Size;

public record CreateDocumentVersionRequest(
        @Size(max = 500)
        String name,

        @Size(max = 150)
        String mimeType
) {
}
