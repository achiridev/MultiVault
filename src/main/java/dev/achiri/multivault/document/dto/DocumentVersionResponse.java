package dev.achiri.multivault.document.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentVersionResponse(
        UUID id,
        Integer versionNumber,
        String name,
        String storageKey,
        String mimeType,
        Long sizeBytes,
        String checksum,
        UUID createdBy,
        Instant createdAt
) {
}
