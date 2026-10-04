package dev.achiri.multivault.infrastructure.security.jwt.exception;

public class UnknownKeyException extends InvalidJwtException {

    public UnknownKeyException(String message) {
        super(message);
    }
}