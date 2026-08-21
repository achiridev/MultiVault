package dev.achiri.multivault.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties(prefix = "multivault.upload")
public record UploadProperties(
        long maxSizeBytes,
        Set<String> allowedMimeTypes
) {

    private static final long DEFAULT_MAX_SIZE_BYTES = 104_857_600L;

    public UploadProperties {
        if (maxSizeBytes <= 0) {
            maxSizeBytes = DEFAULT_MAX_SIZE_BYTES;
        }
        if (allowedMimeTypes == null) {
            allowedMimeTypes = Set.of();
        }
    }
}
