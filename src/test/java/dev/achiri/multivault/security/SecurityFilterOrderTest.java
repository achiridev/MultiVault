package dev.achiri.multivault.security;

import dev.achiri.multivault.infrastructure.persistence.tenant.context.TenantContextFilter;
import dev.achiri.multivault.infrastructure.ratelimit.web.IpRateLimitFilter;
import dev.achiri.multivault.infrastructure.ratelimit.web.TenantRateLimitFilter;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyAuthenticationFilter;
import dev.achiri.multivault.infrastructure.security.jwt.JwtAuthenticationFilter;
import dev.achiri.multivault.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityFilterOrderTest extends BaseIntegrationTest {

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Test
    void securityChainRunsFiltersInExplicitOrder() {
        List<Class<?>> filterClasses = filterChainProxy.getFilterChains().getFirst().getFilters().stream()
                .map(Object::getClass)
                .toList();

        assertThat(filterClasses).containsSubsequence(
                IpRateLimitFilter.class,
                ApiKeyAuthenticationFilter.class,
                JwtAuthenticationFilter.class,
                TenantRateLimitFilter.class,
                TenantContextFilter.class,
                AuthorizationFilter.class);
    }
}
