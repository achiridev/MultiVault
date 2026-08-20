package dev.achiri.multivault.infrastructure.storage.backblaze;

import dev.achiri.multivault.common.exception.StorageException;
import dev.achiri.multivault.infrastructure.storage.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "storage.backblaze-b2", name = "enabled", havingValue = "true")
class BackblazeB2StorageService implements DocumentStorageService {

    private final S3Client s3Client;
    private final BackblazeB2Properties properties;

    @Override
    public void upload(String storageKey, InputStream content, String contentType, long sizeBytes) throws IOException {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(storageKey)
                            .contentType(contentType)
                            .contentLength(sizeBytes)
                            .build(),
                    RequestBody.fromInputStream(content, sizeBytes));
        } catch (S3Exception e) {
            throw new StorageException("Failed to upload document to storage: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String storageKey) throws IOException {
        try {
            return s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(storageKey)
                            .build());
        } catch (S3Exception e) {
            throw new StorageException("Failed to download document from storage: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String storageKey) throws IOException {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(storageKey)
                            .build());
        } catch (S3Exception e) {
            throw new StorageException("Failed to delete document from storage: " + e.getMessage(), e);
        }
    }
}
