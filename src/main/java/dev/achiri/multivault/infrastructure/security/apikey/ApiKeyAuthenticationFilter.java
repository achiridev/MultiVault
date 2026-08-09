package dev.achiri.multivault.infrastructure.security.apikey;

import dev.achiri.multivault.apikey.model.ApiKey;
import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.apikey.repository.ApiKeyRepository;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX_LABEL = "mv_live_";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyHasher apiKeyHasher;
    private final ApiKeyUsageRecorder apiKeyUsageRecorder;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!token.startsWith(KEY_PREFIX_LABEL)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<ApiKey> apiKey = apiKeyRepository.findByKeyHashAndRevokedAtIsNull(apiKeyHasher.sha256Hex(token));
        if (apiKey.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        ApiKey key = apiKey.get();
        if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(Instant.now())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (key.getKeyType() == ApiKeyType.STANDARD) {
            filterChain.doFilter(request, response);
            return;
        }

        apiKeyUsageRecorder.recordUsage(key.getId());

        ApiKeyPrincipal principal = new ApiKeyPrincipal(key.getId(), key.getTenantId(), key.getName(), key.getKeyType());
        List<SimpleGrantedAuthority> authorities = key.getScopes().stream()
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .toList();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
