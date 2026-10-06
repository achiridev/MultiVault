package dev.achiri.multivault.security;

import dev.achiri.multivault.infrastructure.idempotency.web.IdempotencyFilter;
import dev.achiri.multivault.infrastructure.persistence.tenant.context.TenantContextFilter;
import dev.achiri.multivault.infrastructure.ratelimit.web.RateLimitFilter;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyAuthenticationFilter;
import dev.achiri.multivault.infrastructure.security.jwt.JwtAuthenticationFilter;
import dev.achiri.multivault.support.BaseIntegrationTest;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.RegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityFilterRegistrationTest extends BaseIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void securityChainFiltersAreNotAlsoRegisteredInTheServletContainer() {
        assertThat(enabledFilterClasses())
                .doesNotContain(
                        ApiKeyAuthenticationFilter.class,
                        JwtAuthenticationFilter.class,
                        TenantContextFilter.class,
                        RateLimitFilter.class);
    }

    @Test
    void everySecurityChainFilterHasAnExplicitDisabledRegistration() {
        assertThat(disabledFilterClasses())
                .containsExactlyInAnyOrder(
                        ApiKeyAuthenticationFilter.class,
                        JwtAuthenticationFilter.class,
                        TenantContextFilter.class,
                        RateLimitFilter.class,
                        RateLimitFilter.class);
    }

    @Test
    void theOnboardingIdempotencyFilterKeepsItsOwnEnabledRegistration() {
        assertThat(registeredFilters().stream().filter(RegistrationBean::isEnabled).toList())
                .anySatisfy(registration -> {
                    assertThat(registration.getFilter()).isInstanceOf(IdempotencyFilter.class);
                    assertThat(registration.getUrlPatterns()).contains("/api/v1/tenants");
                });
    }

    private List<Class<?>> enabledFilterClasses() {
        return registeredFilters().stream()
                .filter(RegistrationBean::isEnabled)
                .map(SecurityFilterRegistrationTest::filterClass)
                .toList();
    }

    private List<Class<?>> disabledFilterClasses() {
        return registeredFilters().stream()
                .filter(registration -> !registration.isEnabled())
                .map(SecurityFilterRegistrationTest::filterClass)
                .toList();
    }

    private static Class<?> filterClass(FilterRegistrationBean<Filter> registration) {
        return registration.getFilter().getClass();
    }

    private List<FilterRegistrationBean<Filter>> registeredFilters() {
        return new ServletContextInitializerBeans(context, ServletContextInitializer.class).stream()
                .filter(FilterRegistrationBean.class::isInstance)
                .map(ServletContextInitializer.class::cast)
                .map(SecurityFilterRegistrationTest::asFilterRegistration)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static FilterRegistrationBean<Filter> asFilterRegistration(ServletContextInitializer initializer) {
        return (FilterRegistrationBean<Filter>) initializer;
    }
}