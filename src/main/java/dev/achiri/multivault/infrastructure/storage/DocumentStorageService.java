package dev.achiri.multivault.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;

public interface DocumentStorageService {

    void upload(String storageKey, InputStream content, String contentType, long sizeBytes) throws IOException;

    InputStream download(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;
}
