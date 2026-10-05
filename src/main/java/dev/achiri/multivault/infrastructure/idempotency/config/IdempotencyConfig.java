package dev.achiri.multivault.infrastructure.idempotency.config;

import dev.achiri.multivault.infrastructure.idempotency.handler.IdempotencyErrorWriter;
import dev.achiri.multivault.infrastructure.idempotency.spi.IdempotencyStore;
import dev.achiri.multivault.infrastructure.idempotency.web.IdempotencyFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyConfig {

    static final String ONBOARDING_PATH = "/api/v1/tenants";

    @Bean
    IdempotencyFilter idempotencyFilter(IdempotencyStore store, IdempotencyProperties properties,
                                        IdempotencyErrorWriter errorWriter) {
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