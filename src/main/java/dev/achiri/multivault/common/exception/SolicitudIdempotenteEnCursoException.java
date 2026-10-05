package dev.achiri.multivault.common.exception;

public class SolicitudIdempotenteEnCursoException extends RuntimeException {

    public SolicitudIdempotenteEnCursoException(String idempotencyKey) {
        super("Ya hay una solicitud en curso con la clave de idempotencia " + idempotencyKey);
    }
}