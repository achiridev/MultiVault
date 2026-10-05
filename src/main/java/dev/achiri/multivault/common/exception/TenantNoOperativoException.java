package dev.achiri.multivault.common.exception;

import dev.achiri.multivault.tenant.model.TenantStatus;

import java.util.UUID;

public class TenantNoOperativoException extends RuntimeException {

    public TenantNoOperativoException(UUID tenantId, TenantStatus status) {
        super("El tenant " + tenantId + " no está operativo (estado " + status + ")");
    }
}