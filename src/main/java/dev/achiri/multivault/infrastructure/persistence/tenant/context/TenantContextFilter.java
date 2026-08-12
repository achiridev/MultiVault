package dev.achiri.multivault.infrastructure.persistence.tenant.context;

import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyPrincipal;
import dev.achiri.multivault.infrastructure.security.jwt.model.TenantUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenantSchemaResolver tenantSchemaResolver;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            UUID tenantId = resolveTenantId();
            if (tenantId != null) {
                TenantContext.setSchema(tenantSchemaResolver.resolve(tenantId));
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private UUID resolveTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof TenantUserPrincipal tenantUserPrincipal) {
            return tenantUserPrincipal.tenantId();
        }
        if (principal instanceof ApiKeyPrincipal apiKeyPrincipal) {
            return apiKeyPrincipal.tenantId();
        }
        return null;
    }
}
