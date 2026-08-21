package dev.achiri.multivault.common.exception;

public class TipoArchivoNoPermitidoException extends RuntimeException {

    public TipoArchivoNoPermitidoException(String mimeType) {
        super("Tipo de archivo no permitido: " + mimeType);
    }
}
