package dev.achiri.multivault.infrastructure.security;

import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.common.exception.AccesoDenegadoException;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class CurrentTenant {

    private CurrentTenant() {
    }

    public static UUID serviceTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof ApiKeyPrincipal principal
                && principal.keyType() == ApiKeyType.SERVICE) {
            return principal.tenantId();
        }
        throw new AccesoDenegadoException("tenant", "settings");
    }
}
