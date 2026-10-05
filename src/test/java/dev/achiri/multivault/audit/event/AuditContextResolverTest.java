package dev.achiri.multivault.audit.event;

import dev.achiri.multivault.audit.model.ActorType;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyPrincipal;
import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.infrastructure.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditContextResolverTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID API_KEY_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();

    private MockHttpServletRequest request;
    private AuditContextResolver resolver;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");
        resolver = new AuditContextResolver(new StubClientIpResolver());
    }

    @Test
    void recordsClientIpForApiKeyRequests() {
        AuditContext context = resolver.resolve(OWNER_ID, serviceAuthentication(), request);

        assertThat(context.actorType()).isEqualTo(ActorType.API_KEY);
        assertThat(context.tenantId()).isEqualTo(TENANT_ID);
        assertThat(context.apiKeyId()).isEqualTo(API_KEY_ID);
        assertThat(context.ipAddress().getHostAddress()).isEqualTo("203.0.113.9");
    }

    @Test
    void recordsNullIpWhenResolverCannotDetermineIt() {
        resolver = new AuditContextResolver(request -> Optional.empty());

        AuditContext context = resolver.resolve(OWNER_ID, serviceAuthentication(), request);

        assertThat(context.ipAddress()).isNull();
    }

    @Test
    void recordsSystemActorWithoutAuthentication() {
        AuditContext context = resolver.resolve(null, null, request);

        assertThat(context.actorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(context.tenantId()).isNull();
        assertThat(context.ipAddress().getHostAddress()).isEqualTo("203.0.113.9");
    }

    @Test
    void rejectsApiKeyRequestWithoutBodyUserId() {
        Authentication authentication = serviceAuthentication();

        assertThatThrownBy(() -> resolver.resolve(null, authentication, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Authentication serviceAuthentication() {
        ApiKeyPrincipal principal = new ApiKeyPrincipal(API_KEY_ID, TENANT_ID, "onboarding", ApiKeyType.SERVICE);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private record StubClientIpResolver() implements ClientIpResolver {

        @Override
        public Optional<InetAddress> resolve(HttpServletRequest request) {
            try {
                return Optional.of(InetAddress.getByName("203.0.113.9"));
            } catch (UnknownHostException e) {
                return Optional.empty();
            }
        }
    }
}