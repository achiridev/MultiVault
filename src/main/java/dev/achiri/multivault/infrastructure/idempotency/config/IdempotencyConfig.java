package dev.achiri.multivault.infrastructure.idempotency.config;

import dev.achiri.multivault.infrastructure.idempotency.handler.IdempotencyErrorWriter;
import dev.achiri.multivault.infrastructure.idempotency.spi.IdempotencyStore;
import dev.achiri.multivault.infrastructure.idempotency.web.IdempotencyFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyConfig {

    static final String ONBOARDING_PATH = "/api/v1/tenants";

    @Bean
    IdempotencyFilter idempotencyFilter(IdempotencyStore store, IdempotencyProperties properties,
                                        IdempotencyErrorWriter errorWriter) {
        if (properties.enabled()) {
            log.info("Idempotencia activa en {}; inProgressTtl={} completedTtl={}",
                    ONBOARDING_PATH, properties.inProgressTtl(), properties.completedTtl());
        } else {
            log.warn("Idempotencia deshabilitada por multivault.idempotency.enabled=false");
        }
        return new IdempotencyFilter(store, properties, errorWriter);
    }

    @Bean
    FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(IdempotencyFilter idempotencyFilter) {
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(idempotencyFilter);
        registration.addUrlPatterns(ONBOARDING_PATH);
        registration.setOrder(FilterRegistrationBean.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}