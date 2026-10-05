package dev.achiri.multivault.common.exception;

public class ClaveIdempotenciaReutilizadaException extends RuntimeException {

    public ClaveIdempotenciaReutilizadaException(String idempotencyKey) {
        super("La clave de idempotencia " + idempotencyKey
                + " ya se usó con un cuerpo de solicitud diferente");
    }
}