package dev.achiri.multivault.audit.event;

import dev.achiri.multivault.audit.model.ActorType;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyPrincipal;
import dev.achiri.multivault.infrastructure.security.jwt.model.TenantUserPrincipal;
import dev.achiri.multivault.infrastructure.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuditContextResolver {

    private final ClientIpResolver clientIpResolver;

    public AuditContext resolve(UUID bodyUserId, Authentication authentication, HttpServletRequest request) {
        if (authentication != null && authentication.getPrincipal() instanceof TenantUserPrincipal tenantUser) {
            return new AuditContext(
                    tenantUser.tenantId(),
                    tenantUser.memberId(),
                    null,
                    ActorType.TENANT_USER,
                    clientIpResolver.resolve(request).orElse(null),
                    request.getHeader("User-Agent"));
        }
        if (authentication != null && authentication.getPrincipal() instanceof ApiKeyPrincipal apiKey) {
            if (bodyUserId == null) {
                throw new IllegalArgumentException("ownerUserId es requerido para API keys SERVICE");
            }
            return new AuditContext(
                    apiKey.tenantId(),
                    bodyUserId,
                    apiKey.keyId(),
                    ActorType.API_KEY,
                    clientIpResolver.resolve(request).orElse(null),
                    request.getHeader("User-Agent"));
        }
        return new AuditContext(null, null, null, ActorType.SYSTEM,
                clientIpResolver.resolve(request).orElse(null),
                request.getHeader("User-Agent"));
    }
}