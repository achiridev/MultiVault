package dev.achiri.multivault.apikey.controller;

import dev.achiri.multivault.apikey.dto.ApiKeyIssuedResponse;
import dev.achiri.multivault.apikey.dto.ApiKeyResponse;
import dev.achiri.multivault.apikey.dto.CreateApiKeyRequest;
import dev.achiri.multivault.apikey.service.ApiKeyService;
import dev.achiri.multivault.audit.event.AuditContext;
import dev.achiri.multivault.audit.event.AuditContextResolver;
import dev.achiri.multivault.infrastructure.security.CurrentTenant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final AuditContextResolver auditContextResolver;

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_api_keys:manage')")
    public ResponseEntity<ApiKeyIssuedResponse> create(
            @Valid @RequestBody CreateApiKeyRequest requestBody,
            Authentication authentication,
            HttpServletRequest request) {
        UUID tenantId = CurrentTenant.tenantId();
        AuditContext actor = auditContextResolver.resolvePrincipal(authentication, request);
        ApiKeyIssuedResponse issued = apiKeyService.create(requestBody, tenantId, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(issued);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_api_keys:read')")
    public ResponseEntity<List<ApiKeyResponse>> list(
            @RequestParam(name = "includeRevoked", defaultValue = "false") boolean includeRevoked) {
        UUID tenantId = CurrentTenant.tenantId();
        return ResponseEntity.ok(apiKeyService.listByTenant(tenantId, includeRevoked));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_api_keys:manage')")
    public ResponseEntity<Void> revoke(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest request) {
        UUID tenantId = CurrentTenant.tenantId();
        AuditContext actor = auditContextResolver.resolvePrincipal(authentication, request);
        apiKeyService.revoke(tenantId, id, actor);
        return ResponseEntity.noContent().build();
    }
}
