package dev.achiri.multivault.infrastructure.storage.backblaze;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.backblaze-b2")
public record BackblazeB2Properties(
        boolean enabled,
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket
) {
}
