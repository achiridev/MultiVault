package dev.achiri.multivault.document.dto;

import dev.achiri.multivault.document.model.DocumentStatus;

import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String name,
        DocumentStatus status,
        DocumentVersionResponse currentVersion
) {
}
