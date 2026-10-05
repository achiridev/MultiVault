package dev.achiri.multivault.infrastructure.ratelimit.spi;

import jakarta.servlet.http.HttpServletRequest;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;

import java.util.Optional;

@FunctionalInterface
public interface RateLimitKeyResolver {

    Optional<String> resolve(HttpServletRequest request, RateLimitScope scope);
}