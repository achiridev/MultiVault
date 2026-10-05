package dev.achiri.multivault.infrastructure.ratelimit.web;

import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;
import dev.achiri.multivault.infrastructure.ratelimit.spi.RateLimitKeyResolver;
import dev.achiri.multivault.infrastructure.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class IpRateLimitKeyResolver implements RateLimitKeyResolver {

    private static final String GLOBAL_KEY = "shared";

    private final ClientIpResolver clientIpResolver;

    @Override
    public Optional<String> resolve(HttpServletRequest request, RateLimitScope scope) {
        if (scope == RateLimitScope.GLOBAL) {
            return Optional.of(GLOBAL_KEY);
        }
        if (scope != RateLimitScope.IP) {
            return Optional.empty();
        }
        return clientIpResolver.resolve(request).map(InetAddress::getHostAddress);
    }
}