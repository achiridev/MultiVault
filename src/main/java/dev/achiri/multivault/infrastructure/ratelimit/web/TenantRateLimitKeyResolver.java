package dev.achiri.multivault.infrastructure.ratelimit.web;

import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;
import dev.achiri.multivault.infrastructure.ratelimit.spi.RateLimitKeyResolver;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyPrincipal;
import dev.achiri.multivault.infrastructure.security.jwt.model.TenantUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class TenantRateLimitKeyResolver implements RateLimitKeyResolver {

    @Override
    public Optional<String> resolve(HttpServletRequest request, RateLimitScope scope) {
        return principal()
                .flatMap(principal -> switch (scope) {
                    case TENANT -> tenantId(principal);
                    case API_KEY -> apiKeyId(principal);
                    default -> Optional.empty();
                });
    }

    private Optional<Authentication> principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated()
                ? Optional.empty()
                : Optional.of(authentication);
    }

    private Optional<String> tenantId(Authentication authentication) {
        return switch (authentication.getPrincipal()) {
            case TenantUserPrincipal tenantUser -> Optional.of(tenantUser.tenantId().toString());
            case ApiKeyPrincipal apiKey -> Optional.of(apiKey.tenantId().toString());
            default -> Optional.empty();
        };
    }

    private Optional<String> apiKeyId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof ApiKeyPrincipal apiKey) {
            return Optional.of(apiKey.keyId().toString());
        }
        return Optional.empty();
    }
}