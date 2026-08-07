package dev.achiri.multivault.common.exception;

public class TenantProvisioningException extends RuntimeException {

    public TenantProvisioningException(Throwable cause) {
        super("No se pudo aprovisionar el schema del tenant", cause);
    }
}
