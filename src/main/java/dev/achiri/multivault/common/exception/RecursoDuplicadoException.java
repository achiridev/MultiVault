package dev.achiri.multivault.common.exception;

public class RecursoDuplicadoException extends RuntimeException {
    public RecursoDuplicadoException(String campo, String valor) {
        super(campo + " '" + valor + "' ya existe");
    }
}
