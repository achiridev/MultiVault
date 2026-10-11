package dev.achiri.multivault.infrastructure.security;

import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.common.exception.AccesoDenegadoException;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyPrincipal;
import dev.achiri.multivault.infrastructure.security.jwt.model.TenantUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class CurrentTenant {

    private CurrentTenant() {
    }

    public static UUID serviceTenantId() {
        if (principal() instanceof ApiKeyPrincipal principal && principal.keyType() == ApiKeyType.SERVICE) {
            return principal.tenantId();
        }
        throw new AccesoDenegadoException("tenant", "settings");
    }

    public static UUID tenantId() {
        Object principal = principal();
        if (principal instanceof ApiKeyPrincipal apiKeyPrincipal) {
            return apiKeyPrincipal.tenantId();
        }
        if (principal instanceof TenantUserPrincipal tenantUserPrincipal) {
            return tenantUserPrincipal.tenantId();
        }
        throw new AccesoDenegadoException("tenant", "access");
    }

    private static Object principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getPrincipal() : null;
    }
}
