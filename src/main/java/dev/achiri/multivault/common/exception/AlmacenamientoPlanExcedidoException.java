package dev.achiri.multivault.common.exception;

public class AlmacenamientoPlanExcedidoException extends RuntimeException {

    public AlmacenamientoPlanExcedidoException(long storageBytesUsed, long maxStorageBytes) {
        super("El almacenamiento del plan está al límite: " + storageBytesUsed
                + " bytes usados de un máximo de " + maxStorageBytes + " bytes");
    }
}
