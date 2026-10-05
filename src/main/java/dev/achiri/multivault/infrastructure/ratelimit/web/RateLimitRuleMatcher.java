package dev.achiri.multivault.infrastructure.ratelimit.web;

import dev.achiri.multivault.infrastructure.ratelimit.config.RateLimitProperties;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitRule;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class RateLimitRuleMatcher {

    private final List<RateLimitRule> rules;

    public RateLimitRuleMatcher(RateLimitProperties properties) {
        this.rules = properties.rules().stream().map(RateLimitRuleMatcher::toRule).toList();
    }

    public List<RateLimitRule> rules() {
        return rules;
    }

    public Optional<RateLimitRule> find(HttpServletRequest request) {
        return rules.stream().filter(rule -> rule.matches(request)).findFirst();
    }

    private static RateLimitRule toRule(RateLimitProperties.Rule rule) {
        if (rule.id() == null || rule.id().isBlank()) {
            throw new IllegalStateException("toda regla de rate limit necesita un id");
        }
        List<RequestMatcher> matchers = Optional.ofNullable(rule.match())
                .orElse(List.of())
                .stream()
                .map(RateLimitRuleMatcher::toMatcher)
                .toList();
        if (matchers.isEmpty()) {
            throw new IllegalStateException("la regla de rate limit " + rule.id() + " necesita al menos un patrón");
        }
        List<RateLimitScope> scopes = Optional.ofNullable(rule.scopes())
                .orElse(List.of())
                .stream()
                .map(scope -> Enum.valueOf(RateLimitScope.class, scope.trim().toUpperCase()))
                .toList();
        if (rule.spec() == null) {
            throw new IllegalStateException("la regla de rate limit " + rule.id() + " necesita un spec");
        }
        return new RateLimitRule(rule.id(), new OrRequestMatcher(matchers), scopes, rule.spec().toBucketSpec());
    }

    private static RequestMatcher toMatcher(String pattern) {
        int separator = pattern.indexOf(':');
        if (separator < 0) {
            return PathPatternRequestMatcher.withDefaults().matcher(pattern);
        }
        List<HttpMethod> methods = List.of(pattern.substring(0, separator).split(",")).stream()
                .map(method -> HttpMethod.valueOf(method.trim()))
                .toList();
        String path = pattern.substring(separator + 1).trim();
        return methods.stream()
                .map(method -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(method, path))
                .reduce(OrRequestMatcher::new)
                .orElseThrow();
    }
}