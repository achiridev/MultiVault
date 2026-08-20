package dev.achiri.multivault.infrastructure.storage.backblaze;

import dev.achiri.multivault.common.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackblazeB2StorageServiceTest {

    @Mock
    private S3Client s3Client;

    private BackblazeB2Properties properties;
    private BackblazeB2StorageService storageService;

    @BeforeEach
    void setUp() {
        properties = new BackblazeB2Properties(true, "https://s3.us-west-004.backblazeb2.com", "us-west-004", "keyId", "appKey", "my-bucket");
        storageService = new BackblazeB2StorageService(s3Client, properties);
    }

    @Test
    void uploadDelegatesToS3Client() throws IOException {
        InputStream content = new ByteArrayInputStream("test".getBytes());
        storageService.upload("schema/doc/1/checksum", content, "application/pdf", 4L);

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void downloadDelegatesToS3Client() throws IOException {
        ResponseInputStream<GetObjectResponse> responseStream =
                new ResponseInputStream<>(GetObjectResponse.builder().build(),
                        new ByteArrayInputStream("data".getBytes()));
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(responseStream);

        InputStream result = storageService.download("schema/doc/1/checksum");

        assertThat(result).isNotNull();
        verify(s3Client).getObject(any(GetObjectRequest.class));
    }

    @Test
    void deleteDelegatesToS3Client() throws IOException {
        storageService.delete("schema/doc/1/checksum");

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void uploadThrowsStorageExceptionOnS3Error() {
        doThrow(S3Exception.builder().message("Access Denied").build())
                .when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        InputStream content = new ByteArrayInputStream("test".getBytes());

        assertThatThrownBy(() -> storageService.upload("key", content, "type", 4L))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to upload document to storage");
    }

    @Test
    void downloadThrowsStorageExceptionOnS3Error() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("Not Found").build());

        assertThatThrownBy(() -> storageService.download("key"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to download document from storage");
    }

    @Test
    void deleteThrowsStorageExceptionOnS3Error() {
        doThrow(S3Exception.builder().message("Access Denied").build())
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        assertThatThrownBy(() -> storageService.delete("key"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to delete document from storage");
    }
}
