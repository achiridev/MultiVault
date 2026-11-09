package dev.achiri.multivault.infrastructure.security.config;

import dev.achiri.multivault.infrastructure.persistence.tenant.context.TenantContextFilter;
import dev.achiri.multivault.infrastructure.ratelimit.web.IpRateLimitFilter;
import dev.achiri.multivault.infrastructure.ratelimit.web.TenantRateLimitFilter;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyAuthenticationFilter;
import dev.achiri.multivault.infrastructure.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.Filter;
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
    FilterRegistrationBean<IpRateLimitFilter> disableIpRateLimitFilterRegistration(IpRateLimitFilter filter) {
        return disabled(filter);
    }

    @Bean
    FilterRegistrationBean<TenantRateLimitFilter> disableTenantRateLimitFilterRegistration(TenantRateLimitFilter filter) {
        return disabled(filter);
    }

    private static <T extends Filter> FilterRegistrationBean<T> disabled(T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setEnabled(false);
        return registration;
    }
}
