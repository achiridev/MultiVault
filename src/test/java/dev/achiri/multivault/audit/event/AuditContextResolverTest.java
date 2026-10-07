package dev.achiri.multivault.audit.event;

import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.audit.model.ActorType;
import dev.achiri.multivault.common.exception.MiembroInvalidoException;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyPrincipal;
import dev.achiri.multivault.infrastructure.security.jwt.model.TenantUserPrincipal;
import dev.achiri.multivault.infrastructure.web.ClientIpResolver;
import dev.achiri.multivault.tenant.model.TenantMember;
import dev.achiri.multivault.tenant.service.TenantMemberService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditContextResolverTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID API_KEY_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();

    @Mock
    private TenantMemberService tenantMemberService;

    private MockHttpServletRequest request;
    private AuditContextResolver resolver;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");
        resolver = new AuditContextResolver(new StubClientIpResolver(), tenantMemberService);
    }

    @Test
    void recordsClientIpForApiKeyRequests() {
        when(tenantMemberService.upsert(eq(TENANT_ID), anyString(), any(), any()))
                .thenReturn(activeMember());

        AuditContext context = resolver.resolve("sub_actor", null, serviceAuthentication(), request);

        assertThat(context.actorType()).isEqualTo(ActorType.API_KEY);
        assertThat(context.tenantId()).isEqualTo(TENANT_ID);
        assertThat(context.apiKeyId()).isEqualTo(API_KEY_ID);
        assertThat(context.actorUserId()).isEqualTo(MEMBER_ID);
        assertThat(context.ipAddress().getHostAddress()).isEqualTo("203.0.113.9");
    }

    @Test
    void resolvesOwnerSubjectInsideCallerTenant() {
        when(tenantMemberService.upsert(eq(TENANT_ID), eq("sub_actor"), eq("actor@acme.com"), eq(null)))
                .thenReturn(activeMember());

        resolver.resolve("sub_actor", "actor@acme.com", serviceAuthentication(), request);

        verify(tenantMemberService).upsert(TENANT_ID, "sub_actor", "actor@acme.com", null);
    }

    @Test
    void recordsNullIpWhenResolverCannotDetermineIt() {
        resolver = new AuditContextResolver(request -> Optional.empty(), tenantMemberService);
        when(tenantMemberService.upsert(any(), anyString(), any(), any())).thenReturn(activeMember());

        AuditContext context = resolver.resolve("sub_actor", null, serviceAuthentication(), request);

        assertThat(context.ipAddress()).isNull();
    }

    @Test
    void recordsSystemActorWithoutAuthentication() {
        AuditContext context = resolver.resolve(null, null, null, request);

        assertThat(context.actorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(context.tenantId()).isNull();
        assertThat(context.ipAddress().getHostAddress()).isEqualTo("203.0.113.9");
    }

    @Test
    void rejectsApiKeyRequestWithoutOwnerSubject() {
        Authentication authentication = serviceAuthentication();

        assertThatThrownBy(() -> resolver.resolve(null, null, authentication, request))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tenantMemberService, never()).upsert(any(), anyString(), any(), any());
    }

    @Test
    void rejectsApiKeyRequestWhoseMemberIsInactive() {
        TenantMember inactive = activeMember();
        inactive.setIsActive(false);
        when(tenantMemberService.upsert(eq(TENANT_ID), anyString(), any(), any())).thenReturn(inactive);

        assertThatThrownBy(() -> resolver.resolve("sub_actor", null, serviceAuthentication(), request))
                .isInstanceOf(MiembroInvalidoException.class);
    }

    @Test
    void usesPrincipalMemberForTenantUserAndIgnoresOwnerSubject() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new TenantUserPrincipal(MEMBER_ID, TENANT_ID, "sub_actor"), null, List.of());

        AuditContext context = resolver.resolve("sub_otro", "otro@acme.com", authentication, request);

        assertThat(context.actorType()).isEqualTo(ActorType.TENANT_USER);
        assertThat(context.actorUserId()).isEqualTo(MEMBER_ID);
        assertThat(context.apiKeyId()).isNull();
        verify(tenantMemberService, never()).upsert(any(), anyString(), any(), any());
    }

    private TenantMember activeMember() {
        TenantMember member = new TenantMember();
        member.setId(MEMBER_ID);
        member.setTenantId(TENANT_ID);
        member.setSubject("sub_actor");
        member.setIsActive(true);
        return member;
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