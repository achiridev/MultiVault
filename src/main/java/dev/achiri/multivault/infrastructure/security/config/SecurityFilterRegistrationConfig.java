package dev.achiri.multivault.infrastructure.security.config;

import dev.achiri.multivault.infrastructure.persistence.tenant.context.TenantContextFilter;
import dev.achiri.multivault.infrastructure.ratelimit.web.RateLimitFilter;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyAuthenticationFilter;
import dev.achiri.multivault.infrastructure.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityFilterRegistrationConfig {

    @Bean
    FilterRegistrationBean<ApiKeyAuthenticationFilter> disableApiKeyAuthenticationFilterRegistration(
            ApiKeyAuthenticationFilter filter) {
        return disabled(filter);
    }

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> disableJwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        return disabled(filter);
    }

    @Bean
    FilterRegistrationBean<TenantContextFilter> disableTenantContextFilterRegistration(TenantContextFilter filter) {
        return disabled(filter);
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> disableIpRateLimitFilterRegistration(
            @Qualifier("ipRateLimitFilter") RateLimitFilter filter) {
        return disabled(filter);
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> disableTenantRateLimitFilterRegistration(
            @Qualifier("tenantRateLimitFilter") RateLimitFilter filter) {
        return disabled(filter);
    }

    private static <T extends Filter> FilterRegistrationBean<T> disabled(T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setEnabled(false);
        return registration;
    }
}
