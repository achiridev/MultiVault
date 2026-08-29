package dev.achiri.multivault.security;

import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyAuthenticationFilter;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyIdentity;
import dev.achiri.multivault.infrastructure.security.jwt.JwtAuthenticationFilter;
import dev.achiri.multivault.infrastructure.security.jwt.MultiIssuerJwtDecoder;
import dev.achiri.multivault.infrastructure.security.jwt.exception.InvalidJwtException;
import dev.achiri.multivault.infrastructure.security.jwt.model.TenantUserPrincipal;
import dev.achiri.multivault.infrastructure.security.jwt.model.ValidatedJwt;
import dev.achiri.multivault.tenant.model.TenantMember;
import dev.achiri.multivault.tenant.service.TenantMemberService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock
    private MultiIssuerJwtDecoder jwtDecoder;

    @Mock
    private TenantMemberService tenantMemberService;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtDecoder, tenantMemberService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsStandardKeyScopesAsAuthorities() throws Exception {
        MockHttpServletRequest request = bearerRequest("some.jwt.token");
        ApiKeyIdentity standardKey = new ApiKeyIdentity(
                UUID.randomUUID(), TENANT_ID, "standard", ApiKeyType.STANDARD,
                List.of("documents:read", "documents:write"), 0);
        request.setAttribute(ApiKeyAuthenticationFilter.STANDARD_API_KEY_ATTR, standardKey);
        FilterChain chain = mock(FilterChain.class);
        when(jwtDecoder.authenticate("some.jwt.token"))
                .thenReturn(new ValidatedJwt(TENANT_ID, "user-1", "user@test.com", "User One"));
        TenantMember member = new TenantMember();
        member.setId(UUID.randomUUID());
        member.setTenantId(TENANT_ID);
        member.setSubject("user-1");
        when(tenantMemberService.upsert(TENANT_ID, "user-1", "user@test.com", "User One")).thenReturn(member);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities()).extracting(authority -> authority.getAuthority())
                .containsExactly("SCOPE_documents:read", "SCOPE_documents:write");
        assertThat(authentication.getPrincipal()).isInstanceOf(TenantUserPrincipal.class)
                .satisfies(principal -> {
                    TenantUserPrincipal userPrincipal = (TenantUserPrincipal) principal;
                    assertThat(userPrincipal.memberId()).isEqualTo(member.getId());
                    assertThat(userPrincipal.tenantId()).isEqualTo(TENANT_ID);
                    assertThat(userPrincipal.subject()).isEqualTo("user-1");
                });
        verify(chain).doFilter(any(), any());
    }

    @Test
    void doesNotAuthenticateJwtWithoutStandardKey() throws Exception {
        MockHttpServletRequest request = bearerRequest("some.jwt.token");
        FilterChain chain = mock(FilterChain.class);
        when(jwtDecoder.authenticate("some.jwt.token"))
                .thenReturn(new ValidatedJwt(TENANT_ID, "user-1", "user@test.com", "User One"));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tenantMemberService, never()).upsert(any(), any(), any(), any());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void clearsContextOnInvalidJwt() throws Exception {
        MockHttpServletRequest request = bearerRequest("bad.token");
        FilterChain chain = mock(FilterChain.class);
        when(jwtDecoder.authenticate("bad.token")).thenThrow(new InvalidJwtException("JWT inválido"));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tenantMemberService, never()).upsert(any(), any(), any(), any());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void skipsDecodingForApiKeyToken() throws Exception {
        MockHttpServletRequest request = bearerRequest(
                "mv_live_abcdef1234567890abcdef1234567890abcdef12345678");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(jwtDecoder, never()).authenticate(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void skipsWhenAlreadyAuthenticated() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("principal", null, List.of()));
        MockHttpServletRequest request = bearerRequest("some.jwt.token");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(jwtDecoder, never()).authenticate(anyString());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void skipsAuthenticationWhenStandardKeyAndJwtTenantsDiffer() throws Exception {
        MockHttpServletRequest request = bearerRequest("some.jwt.token");
        ApiKeyIdentity standardKey = new ApiKeyIdentity(
                UUID.randomUUID(), UUID.randomUUID(), "standard", ApiKeyType.STANDARD, List.of(), 0);
        request.setAttribute(ApiKeyAuthenticationFilter.STANDARD_API_KEY_ATTR, standardKey);
        FilterChain chain = mock(FilterChain.class);
        when(jwtDecoder.authenticate("some.jwt.token"))
                .thenReturn(new ValidatedJwt(TENANT_ID, "user-1", "user@test.com", "User One"));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tenantMemberService, never()).upsert(any(), any(), any(), any());
        verify(chain).doFilter(any(), any());
    }

    private MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
