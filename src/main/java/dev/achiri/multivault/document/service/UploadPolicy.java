package dev.achiri.multivault.document.service;

import dev.achiri.multivault.common.exception.ArchivoDemasiadoGrandeException;
import dev.achiri.multivault.common.exception.ArchivoInvalidoException;
import dev.achiri.multivault.common.exception.TipoArchivoNoPermitidoException;
import dev.achiri.multivault.document.config.UploadProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class UploadPolicy {

    private final UploadProperties properties;

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ArchivoInvalidoException("El archivo está vacío o ausente");
        }
        if (file.getSize() > properties.maxSizeBytes()) {
            throw new ArchivoDemasiadoGrandeException(file.getSize(), properties.maxSizeBytes());
        }
    }

    public void validateMimeType(String mimeType) {
        if (!properties.allowedMimeTypes().isEmpty() && !properties.allowedMimeTypes().contains(mimeType)) {
            throw new TipoArchivoNoPermitidoException(mimeType);
        }
    }
}
