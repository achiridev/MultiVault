package dev.achiri.multivault.infrastructure.ratelimit.web;

import dev.achiri.multivault.infrastructure.ratelimit.config.RateLimitProperties;
import dev.achiri.multivault.infrastructure.ratelimit.handler.JsonErrorWriter;
import dev.achiri.multivault.infrastructure.ratelimit.listener.RateLimitMetricsListener;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitDecision;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitRule;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;
import dev.achiri.multivault.infrastructure.ratelimit.spi.RateLimitKeyResolver;
import dev.achiri.multivault.infrastructure.ratelimit.spi.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    static final String LIMIT_HEADER = "RateLimit-Limit";
    static final String REMAINING_HEADER = "RateLimit-Remaining";
    static final String RESET_HEADER = "RateLimit-Reset";
    static final String RETRY_AFTER_HEADER = "Retry-After";

    private final RateLimitRuleMatcher ruleMatcher;
    private final RateLimiter rateLimiter;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitMetricsListener metrics;
    private final JsonErrorWriter jsonErrorWriter;
    private final Set<RateLimitScope> handledScopes;
    private final RateLimitProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        List<RateLimitRule> matched = ruleMatcher.findAll(request).stream()
                .filter(rule -> rule.scopes().stream().anyMatch(handledScopes::contains))
                .toList();
        for (RateLimitRule rule : matched) {
            Optional<RateLimitDecision> rejection = decide(request, rule);
            if (rejection.isPresent()) {
                reject(request, response, rejection.get());
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private Optional<RateLimitDecision> decide(HttpServletRequest request, RateLimitRule rule) {
        for (RateLimitScope scope : rule.scopes()) {
            Optional<String> key = keyResolver.resolve(request, scope);
            if (key.isEmpty()) {
                continue;
            }
            RateLimitDecision decision = rateLimiter.consume(key.get(), scope, rule.id(), rule.spec());
            if (!decision.allowed()) {
                return Optional.of(decision);
            }
            metrics.recordConsumed(rule.id());
        }
        return Optional.empty();
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, RateLimitDecision decision)
            throws IOException {

        metrics.recordRejected(decision.ruleId());
        writeRateLimitHeaders(response, decision);
        response.setHeader(RETRY_AFTER_HEADER, String.valueOf(decision.retryAfterSeconds()));
        log.warn("Request rechazado por rate limiting rule={} scope={} method={} path={}",
                decision.ruleId(), decision.scope(), request.getMethod(), request.getRequestURI());
        jsonErrorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                "Demasiadas solicitudes. Reintenta más tarde.");
    }

    private void writeRateLimitHeaders(HttpServletResponse response, RateLimitDecision decision) {
        response.setHeader(LIMIT_HEADER, String.valueOf(decision.limit()));
        response.setHeader(REMAINING_HEADER, String.valueOf(decision.remaining()));
        response.setHeader(RESET_HEADER, String.valueOf(decision.retryAfterSeconds()));
    }
}