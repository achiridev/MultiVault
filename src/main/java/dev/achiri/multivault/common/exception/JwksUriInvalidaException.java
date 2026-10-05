package dev.achiri.multivault.common.exception;

public class JwksUriInvalidaException extends RuntimeException {

    public JwksUriInvalidaException(String mensaje) {
        super(mensaje);
    }

    public JwksUriInvalidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}