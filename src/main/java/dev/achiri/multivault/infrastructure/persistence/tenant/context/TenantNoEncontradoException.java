package dev.achiri.multivault.infrastructure.persistence.tenant.context;

import java.util.UUID;

public class TenantNoEncontradoException extends RuntimeException {

    public TenantNoEncontradoException(UUID tenantId) {
        super("tenant " + tenantId + " no encontrado");
    }
}