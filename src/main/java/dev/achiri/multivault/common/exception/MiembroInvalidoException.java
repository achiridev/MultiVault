package dev.achiri.multivault.common.exception;

import java.util.UUID;

public class MiembroInvalidoException extends RuntimeException {

    public MiembroInvalidoException(UUID tenantId, String subject) {
        super("El subject '" + subject + "' corresponde a un miembro inactivo del tenant " + tenantId);
    }
}