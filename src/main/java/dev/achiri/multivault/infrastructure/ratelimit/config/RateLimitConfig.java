package dev.achiri.multivault.infrastructure.ratelimit.config;

import dev.achiri.multivault.infrastructure.ratelimit.handler.JsonErrorWriter;
import dev.achiri.multivault.infrastructure.ratelimit.listener.RateLimitMetricsListener;
import dev.achiri.multivault.infrastructure.ratelimit.redis.RedisRateLimiter;
import dev.achiri.multivault.infrastructure.ratelimit.resilience.FailModeRateLimiter;
import dev.achiri.multivault.infrastructure.ratelimit.resilience.LocalRateLimiter;
import dev.achiri.multivault.infrastructure.ratelimit.spi.RateLimiter;
import dev.achiri.multivault.infrastructure.ratelimit.web.IpRateLimitFilter;
import dev.achiri.multivault.infrastructure.ratelimit.web.IpRateLimitKeyResolver;
import dev.achiri.multivault.infrastructure.ratelimit.web.RateLimitRuleMatcher;
import dev.achiri.multivault.infrastructure.ratelimit.web.TenantRateLimitFilter;
import dev.achiri.multivault.infrastructure.ratelimit.web.TenantRateLimitKeyResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    LocalRateLimiter localRateLimiter() {
        return new LocalRateLimiter();
    }

    @Bean
    RateLimiter rateLimiter(RedisRateLimiter distributed, LocalRateLimiter localRateLimiter,
                            RateLimitProperties properties) {
        List<String> errors = properties.validationErrors();
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Configuración inválida de rate limiting: " + String.join("; ", errors));
        }
        if (properties.enabled()) {
            log.info("Rate limiting activo; failMode={} reglas={}", properties.failMode(), properties.rules().size());
        } else {
            log.warn("Rate limiting deshabilitado por multivault.ratelimit.enabled=false");
        }
        return new FailModeRateLimiter(distributed, localRateLimiter, properties.failMode());
    }

    @Bean
    IpRateLimitFilter ipRateLimitFilter(RateLimitRuleMatcher ruleMatcher, RateLimiter rateLimiter,
                                        IpRateLimitKeyResolver keyResolver, RateLimitMetricsListener metrics,
                                        JsonErrorWriter jsonErrorWriter, RateLimitProperties properties) {
        return new IpRateLimitFilter(ruleMatcher, rateLimiter, keyResolver, metrics, jsonErrorWriter, properties);
    }

    @Bean
    TenantRateLimitFilter tenantRateLimitFilter(RateLimitRuleMatcher ruleMatcher, RateLimiter rateLimiter,
                                                TenantRateLimitKeyResolver keyResolver, RateLimitMetricsListener metrics,
                                                JsonErrorWriter jsonErrorWriter, RateLimitProperties properties) {
        return new TenantRateLimitFilter(ruleMatcher, rateLimiter, keyResolver, metrics, jsonErrorWriter, properties);
    }
}