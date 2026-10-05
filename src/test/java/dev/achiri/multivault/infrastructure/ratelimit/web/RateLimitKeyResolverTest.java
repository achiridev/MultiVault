package dev.achiri.multivault.infrastructure.ratelimit.web;

import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyPrincipal;
import dev.achiri.multivault.apikey.model.ApiKeyType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitKeyResolverTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID KEY_ID = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ipResolverReturnsClientIpForIpScope() {
        IpRateLimitKeyResolver resolver = new IpRateLimitKeyResolver(request -> Optional.of(address("203.0.113.5")));

        assertThat(resolver.resolve(new MockHttpServletRequest(), RateLimitScope.IP)).contains("203.0.113.5");
    }

    @Test
    void ipResolverReturnsSharedKeyForGlobalScope() {
        IpRateLimitKeyResolver resolver = new IpRateLimitKeyResolver(request -> Optional.of(address("203.0.113.5")));

        assertThat(resolver.resolve(new MockHttpServletRequest(), RateLimitScope.GLOBAL)).contains("shared");
    }

    @Test
    void ipResolverIgnoresTenantAndApiKeyScopes() {
        IpRateLimitKeyResolver resolver = new IpRateLimitKeyResolver(request -> Optional.of(address("203.0.113.5")));

        assertThat(resolver.resolve(new MockHttpServletRequest(), RateLimitScope.TENANT)).isEmpty();
        assertThat(resolver.resolve(new MockHttpServletRequest(), RateLimitScope.API_KEY)).isEmpty();
    }

    @Test
    void ipResolverSkipsIpScopeWhenClientIpCannotBeResolved() {
        IpRateLimitKeyResolver resolver = new IpRateLimitKeyResolver(request -> Optional.empty());

        assertThat(resolver.resolve(new MockHttpServletRequest(), RateLimitScope.IP)).isEmpty();
    }

    @Test
    void tenantResolverReturnsTenantIdForApiKeyPrincipal() {
        authenticate(new ApiKeyPrincipal(KEY_ID, TENANT_ID, "onboarding", ApiKeyType.SERVICE));
        TenantRateLimitKeyResolver resolver = new TenantRateLimitKeyResolver();

        assertThat(resolver.resolve(new MockHttpServletRequest(), RateLimitScope.TENANT))
                .contains(TENANT_ID.toString());
    }

    @Test
    void tenantResolverReturnsKeyIdForApiKeyScope() {
        authenticate(new ApiKeyPrincipal(KEY_ID, TENANT_ID, "onboarding", ApiKeyType.SERVICE));
        TenantRateLimitKeyResolver resolver = new TenantRateLimitKeyResolver();

        assertThat(resolver.resolve(new MockHttpServletRequest(), RateLimitScope.API_KEY))
                .contains(KEY_ID.toString());
    }

    @Test
    void tenantResolverIgnoresIpAndGlobalScopes() {
        authenticate(new ApiKeyPrincipal(KEY_ID, TENANT_ID, "onboarding", ApiKeyType.SERVICE));
        TenantRateLimitKeyResolver resolver = new TenantRateLimitKeyResolver();

        assertThat(resolver.resolve(new MockHttpServletRequest(), RateLimitScope.IP)).isEmpty();
        assertThat(resolver.resolve(new MockHttpServletRequest(), RateLimitScope.GLOBAL)).isEmpty();
    }

    @Test
    void tenantResolverReturnsNothingWithoutAuthentication() {
        TenantRateLimitKeyResolver resolver = new TenantRateLimitKeyResolver();

        assertThat(resolver.resolve(new MockHttpServletRequest(), RateLimitScope.TENANT)).isEmpty();
        assertThat(resolver.resolve(new MockHttpServletRequest(), RateLimitScope.API_KEY)).isEmpty();
    }

    @Test
    void tenantResolverHasNoKeyForJwtPrincipalOnApiKeyScope() {
        authenticate(new dev.achiri.multivault.infrastructure.security.jwt.model.TenantUserPrincipal(
                UUID.randomUUID(), TENANT_ID, "user"));
        TenantRateLimitKeyResolver resolver = new TenantRateLimitKeyResolver();

        assertThat(resolver.resolve(new MockHttpServletRequest(), RateLimitScope.API_KEY)).isEmpty();
    }

    private static void authenticate(Object principal) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static InetAddress address(String value) {
        try {
            return InetAddress.getByName(value);
        } catch (java.net.UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }
}