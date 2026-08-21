package dev.achiri.multivault.infrastructure.storage;

import dev.achiri.multivault.common.exception.StorageException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
@ConditionalOnProperty(prefix = "storage.backblaze-b2", name = "enabled", havingValue = "false", matchIfMissing = true)
public class UnconfiguredDocumentStorageService implements DocumentStorageService {

    @Override
    public void upload(String storageKey, InputStream content, String contentType, long sizeBytes) throws IOException {
        throw new StorageException("El almacenamiento de documentos no está configurado");
    }

    @Override
    public InputStream download(String storageKey) throws IOException {
        throw new StorageException("El almacenamiento de documentos no está configurado");
    }

    @Override
    public void delete(String storageKey) throws IOException {
        throw new StorageException("El almacenamiento de documentos no está configurado");
    }
}
