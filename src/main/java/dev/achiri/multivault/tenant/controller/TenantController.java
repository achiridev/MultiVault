package dev.achiri.multivault.tenant.controller;

import dev.achiri.multivault.tenant.dto.CreateTenantRequest;
import dev.achiri.multivault.tenant.dto.CreateTenantResponse;
import dev.achiri.multivault.tenant.dto.TenantStatusResponse;
import dev.achiri.multivault.tenant.dto.UpdateTenantIdentityProviderRequest;
import dev.achiri.multivault.tenant.dto.UpdateTenantStatusRequest;
import dev.achiri.multivault.tenant.service.TenantLifecycleService;
import dev.achiri.multivault.tenant.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {
    private final TenantService tenantService;
    private final TenantLifecycleService tenantLifecycleService;

    @PostMapping
    public ResponseEntity<CreateTenantResponse> create(@Valid @RequestBody CreateTenantRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(tenantService.create(request));
    }

    @PutMapping("/{tenantId}/identity-provider")
    @PreAuthorize("hasAuthority('SCOPE_tenant:settings:write')")
    public ResponseEntity<CreateTenantResponse.TenantIdentityProviderDto> updateIdentityProvider(
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateTenantIdentityProviderRequest request) {
        return ResponseEntity.ok(tenantService.updateIdentityProvider(tenantId, request));
    }

    @PutMapping("/{tenantId}/status")
    @PreAuthorize("hasAuthority('SCOPE_tenant:settings:write')")
    public ResponseEntity<TenantStatusResponse> updateStatus(
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateTenantStatusRequest request) {
        return ResponseEntity.ok(tenantLifecycleService.updateStatus(tenantId, request));
    }
}
