package dev.achiri.multivault.infrastructure.ratelimit.web;

import dev.achiri.multivault.infrastructure.ratelimit.config.RateLimitProperties;
import dev.achiri.multivault.infrastructure.ratelimit.handler.JsonErrorWriter;
import dev.achiri.multivault.infrastructure.ratelimit.listener.RateLimitMetricsListener;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;
import dev.achiri.multivault.infrastructure.ratelimit.spi.RateLimiter;

import java.util.Set;

public class IpRateLimitFilter extends RateLimitFilter {

    public IpRateLimitFilter(
            RateLimitRuleMatcher ruleMatcher,
            RateLimiter rateLimiter,
            IpRateLimitKeyResolver keyResolver,
            RateLimitMetricsListener metrics,
            JsonErrorWriter jsonErrorWriter,
            RateLimitProperties properties) {
        super(ruleMatcher, rateLimiter, keyResolver, metrics, jsonErrorWriter,
                Set.of(RateLimitScope.IP, RateLimitScope.GLOBAL), properties);
    }
}
