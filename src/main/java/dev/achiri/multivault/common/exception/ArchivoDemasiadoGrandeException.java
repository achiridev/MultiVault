package dev.achiri.multivault.common.exception;

public class ArchivoDemasiadoGrandeException extends RuntimeException {

    public ArchivoDemasiadoGrandeException(long sizeBytes, long maxBytes) {
        super("El archivo excede el tamaño máximo permitido: " + sizeBytes + " bytes (máximo " + maxBytes + " bytes)");
    }
}
