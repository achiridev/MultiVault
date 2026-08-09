package dev.achiri.multivault.infrastructure.security.apikey;

import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.apikey.service.ApiKeyHasher;
import dev.achiri.multivault.apikey.service.ApiKeyUsageRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String STANDARD_API_KEY_ATTR = ApiKeyAuthenticationFilter.class.getName() + ".STANDARD";

    private static final String KEY_PREFIX_LABEL = "mv_live_";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyAuthenticator apiKeyAuthenticator;
    private final ApiKeyHasher apiKeyHasher;
    private final ApiKeyUsageRecorder apiKeyUsageRecorder;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        ApiKeyIdentity key = resolveApiKey(request);
        if (key == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (key.isExpired()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (key.keyType() == ApiKeyType.STANDARD) {
            request.setAttribute(STANDARD_API_KEY_ATTR, key);
            filterChain.doFilter(request, response);
            return;
        }

        apiKeyUsageRecorder.recordUsage(key.keyId());

        ApiKeyPrincipal principal = new ApiKeyPrincipal(key.keyId(), key.tenantId(), key.name(), key.keyType());
        List<SimpleGrantedAuthority> authorities = key.scopes().stream()
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .toList();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private ApiKeyIdentity resolveApiKey(HttpServletRequest request) {
        String token = null;
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            token = authorization.substring(BEARER_PREFIX.length()).trim();
        }
        if (token == null || !token.startsWith(KEY_PREFIX_LABEL)) {
            String apiKeyHeader = request.getHeader(API_KEY_HEADER);
            if (apiKeyHeader != null && apiKeyHeader.startsWith(KEY_PREFIX_LABEL)) {
                token = apiKeyHeader.trim();
            }
        }
        if (token == null || !token.startsWith(KEY_PREFIX_LABEL)) {
            return null;
        }
        return apiKeyAuthenticator.findValidByHash(apiKeyHasher.sha256Hex(token));
    }
}
