package dev.achiri.multivault.infrastructure.persistence.tenant.context;

import dev.achiri.multivault.common.exception.TenantNoOperativoException;
import dev.achiri.multivault.infrastructure.ratelimit.handler.JsonErrorWriter;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyPrincipal;
import dev.achiri.multivault.infrastructure.security.jwt.model.TenantUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import dev.achiri.multivault.tenant.model.TenantStatus;
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

    private static final String RETRY_AFTER_HEADER = "Retry-After";
    private static final String PROVISIONING_RETRY_AFTER = "2";

    private final TenantSchemaResolver tenantSchemaResolver;
    private final JsonErrorWriter jsonErrorWriter;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            UUID tenantId = resolveTenantId();
            if (tenantId != null) {
                bindTenant(request, tenantId, response, filterChain);
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void bindTenant(HttpServletRequest request, UUID tenantId, HttpServletResponse response,
                            FilterChain filterChain) throws ServletException, IOException {

        try {
            TenantSchemaRef schema = tenantSchemaResolver.resolve(tenantId);
            if (schema.status() != TenantStatus.ACTIVE) {
                writeNotOperational(response, tenantId, schema.status());
                return;
            }
            TenantContext.setSchema(schema.schemaName());
            filterChain.doFilter(request, response);
        } catch (TenantNoEncontradoException e) {
            jsonErrorWriter.write(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        }
    }

    private void writeNotOperational(HttpServletResponse response, UUID tenantId, TenantStatus status)
            throws IOException {

        if (status == TenantStatus.PENDING_PROVISIONING) {
            response.setHeader(RETRY_AFTER_HEADER, PROVISIONING_RETRY_AFTER);
        }
        jsonErrorWriter.write(response, HttpServletResponse.SC_CONFLICT,
                new TenantNoOperativoException(tenantId, status).getMessage());
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