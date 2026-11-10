package dev.achiri.multivault.infrastructure.ratelimit.web;

import dev.achiri.multivault.infrastructure.ratelimit.config.RateLimitProperties;
import dev.achiri.multivault.infrastructure.ratelimit.handler.JsonErrorWriter;
import dev.achiri.multivault.infrastructure.ratelimit.listener.RateLimitMetricsListener;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;
import dev.achiri.multivault.infrastructure.ratelimit.spi.RateLimiter;

import java.util.Set;

public class TenantRateLimitFilter extends RateLimitFilter {

    public TenantRateLimitFilter(
            RateLimitRuleMatcher ruleMatcher,
            RateLimiter rateLimiter,
            TenantRateLimitKeyResolver keyResolver,
            RateLimitMetricsListener metrics,
            JsonErrorWriter jsonErrorWriter,
            RateLimitProperties properties) {
        super(ruleMatcher, rateLimiter, keyResolver, metrics, jsonErrorWriter,
                Set.of(RateLimitScope.TENANT, RateLimitScope.API_KEY), properties);
    }
}
