package dev.achiri.multivault.infrastructure.security.jwt;

import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyAuthenticationFilter;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyIdentity;
import dev.achiri.multivault.infrastructure.security.jwt.exception.InvalidJwtException;
import dev.achiri.multivault.infrastructure.security.jwt.model.TenantUserPrincipal;
import dev.achiri.multivault.infrastructure.security.jwt.model.ValidatedJwt;
import dev.achiri.multivault.tenant.model.TenantMember;
import dev.achiri.multivault.tenant.service.TenantMemberService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String KEY_PREFIX_LABEL = "mv_live_";

    private final MultiIssuerJwtDecoder jwtDecoder;
    private final TenantMemberService tenantMemberService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing != null && existing.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractBearerToken(request);
        if (token == null || token.startsWith(KEY_PREFIX_LABEL)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            ValidatedJwt validated = jwtDecoder.authenticate(token);

            ApiKeyIdentity standardKey =
                    (ApiKeyIdentity) request.getAttribute(ApiKeyAuthenticationFilter.STANDARD_API_KEY_ATTR);
            if (standardKey == null || !standardKey.tenantId().equals(validated.tenantId())) {
                filterChain.doFilter(request, response);
                return;
            }

            TenantMember member = tenantMemberService.upsert(
                    validated.tenantId(), validated.subject(), validated.email(), validated.displayName());

            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            if (standardKey != null) {
                standardKey.scopes().forEach(scope -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope)));
            }

            TenantUserPrincipal principal =
                    new TenantUserPrincipal(member.getId(), member.getTenantId(), member.getSubject());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (InvalidJwtException e) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }
}
