package dev.achiri.multivault.common.exception;

import dev.achiri.multivault.tenant.model.TenantStatus;

public class EstadoTransicionInvalidoException extends RuntimeException {
    public EstadoTransicionInvalidoException(TenantStatus from, TenantStatus to) {
        super("Transición de estado inválida: " + from + " → " + to);
    }
}
