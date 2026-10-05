package dev.achiri.multivault.infrastructure.ratelimit.model;

import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

public record RateLimitRule(
        String id,
        RequestMatcher matcher,
        List<RateLimitScope> scopes,
        BucketSpec spec
) {

    public RateLimitRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id de regla requerido");
        }
        if (matcher == null) {
            throw new IllegalArgumentException("matcher requerido en la regla " + id);
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec requerido en la regla " + id);
        }
        scopes = List.copyOf(scopes);
        if (scopes.isEmpty()) {
            throw new IllegalArgumentException("la regla " + id + " necesita al menos un scope");
        }
    }

    public boolean matches(jakarta.servlet.http.HttpServletRequest request) {
        return matcher.matches(request);
    }
}