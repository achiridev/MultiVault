package dev.achiri.multivault.common.exception;

public class UnknownKeyException extends InvalidJwtException {

    public UnknownKeyException(String message) {
        super(message);
    }
}