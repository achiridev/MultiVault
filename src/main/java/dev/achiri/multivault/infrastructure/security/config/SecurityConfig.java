package dev.achiri.multivault.infrastructure.security.config;

import dev.achiri.multivault.infrastructure.persistence.tenant.context.TenantContextFilter;
import dev.achiri.multivault.infrastructure.ratelimit.web.IpRateLimitFilter;
import dev.achiri.multivault.infrastructure.ratelimit.web.TenantRateLimitFilter;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyAuthenticationFilter;
import dev.achiri.multivault.infrastructure.security.handler.RestAuthenticationEntryPoint;
import dev.achiri.multivault.infrastructure.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.Filter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            TenantContextFilter tenantContextFilter,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            IpRateLimitFilter ipRateLimitFilter,
            TenantRateLimitFilter tenantRateLimitFilter) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(restAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/tenants/identity-provider").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/tenants/status").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/documents").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/documents/*/versions").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/documents/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/api-keys").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/api-keys").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/api-keys/*").authenticated()
                        .anyRequest().authenticated());

        registerSecurityFiltersInExecutionOrder(http, List.of(
                ipRateLimitFilter,
                apiKeyAuthenticationFilter,
                jwtAuthenticationFilter,
                tenantRateLimitFilter,
                tenantContextFilter));
        return http.build();
    }

    private static void registerSecurityFiltersInExecutionOrder(HttpSecurity http, List<Filter> executionOrder) {
        if (executionOrder.stream().map(Object::getClass).distinct().count() != executionOrder.size()) {
            throw new IllegalStateException("Los filtros de la cadena de seguridad deben ser de clases distintas");
        }

        Class<? extends Filter> anchor = UsernamePasswordAuthenticationFilter.class;
        for (int i = executionOrder.size() - 1; i >= 0; i--) {
            Filter filter = executionOrder.get(i);
            http.addFilterBefore(filter, anchor);
            anchor = filter.getClass();
        }
    }
}