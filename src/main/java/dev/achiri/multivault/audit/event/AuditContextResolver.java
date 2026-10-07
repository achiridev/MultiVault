package dev.achiri.multivault.audit.event;

import dev.achiri.multivault.audit.model.ActorType;
import dev.achiri.multivault.common.exception.MiembroInvalidoException;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyPrincipal;
import dev.achiri.multivault.infrastructure.security.jwt.model.TenantUserPrincipal;
import dev.achiri.multivault.infrastructure.web.ClientIpResolver;
import dev.achiri.multivault.tenant.model.TenantMember;
import dev.achiri.multivault.tenant.service.TenantMemberService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditContextResolver {

    private final ClientIpResolver clientIpResolver;
    private final TenantMemberService tenantMemberService;

    public AuditContext resolve(String actorSubject, String actorEmail,
                                Authentication authentication, HttpServletRequest request) {
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
            if (actorSubject == null) {
                throw new IllegalArgumentException("ownerSubject es requerido para API keys SERVICE");
            }
            TenantMember actor = tenantMemberService.upsert(apiKey.tenantId(), actorSubject, actorEmail, null);
            if (!Boolean.TRUE.equals(actor.getIsActive())) {
                throw new MiembroInvalidoException(apiKey.tenantId(), actorSubject);
            }
            return new AuditContext(
                    apiKey.tenantId(),
                    actor.getId(),
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