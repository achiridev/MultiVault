package dev.achiri.multivault.infrastructure.storage.backblaze;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(BackblazeB2Properties.class)
@ConditionalOnProperty(prefix = "storage.backblaze-b2", name = "enabled", havingValue = "true")
class BackblazeB2Config {

    @Bean
    S3Client s3Client(BackblazeB2Properties properties) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.accessKey(), properties.secretKey());

        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(true)
                .build();
    }

    @Bean
    S3Presigner s3Presigner(BackblazeB2Properties properties) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.accessKey(), properties.secretKey());

        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}
