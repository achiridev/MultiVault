package dev.achiri.multivault.security;

import dev.achiri.multivault.apikey.model.ApiKeyType;
import dev.achiri.multivault.common.exception.AccesoDenegadoException;
import dev.achiri.multivault.infrastructure.security.CurrentTenant;
import dev.achiri.multivault.infrastructure.security.apikey.ApiKeyPrincipal;
import dev.achiri.multivault.infrastructure.security.jwt.model.TenantUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentTenantTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tenantIdResolvesFromServiceApiKeyPrincipal() {
        UUID tenantId = UUID.randomUUID();
        ApiKeyPrincipal principal = new ApiKeyPrincipal(UUID.randomUUID(), tenantId, "Root Key", ApiKeyType.SERVICE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList()));

        assertThat(CurrentTenant.tenantId()).isEqualTo(tenantId);
    }

    @Test
    void tenantIdResolvesFromTenantUserPrincipal() {
        UUID tenantId = UUID.randomUUID();
        TenantUserPrincipal principal = new TenantUserPrincipal(UUID.randomUUID(), tenantId, "user-subject");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList()));

        assertThat(CurrentTenant.tenantId()).isEqualTo(tenantId);
    }

    @Test
    void tenantIdThrowsWhenNoAuthenticationPresent() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(CurrentTenant::tenantId)
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    void serviceTenantIdAcceptsOnlyServicePrincipal() {
        UUID tenantId = UUID.randomUUID();
        ApiKeyPrincipal servicePrincipal = new ApiKeyPrincipal(UUID.randomUUID(), tenantId, "Root Key", ApiKeyType.SERVICE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(servicePrincipal, null, Collections.emptyList()));

        assertThat(CurrentTenant.serviceTenantId()).isEqualTo(tenantId);

        TenantUserPrincipal userPrincipal = new TenantUserPrincipal(UUID.randomUUID(), tenantId, "user-subject");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userPrincipal, null, Collections.emptyList()));

        assertThatThrownBy(CurrentTenant::serviceTenantId)
                .isInstanceOf(AccesoDenegadoException.class);
    }
}
