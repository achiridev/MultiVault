package dev.achiri.multivault.document.controller;

import dev.achiri.multivault.document.dto.CreateDocumentRequest;
import dev.achiri.multivault.document.dto.CreateDocumentVersionRequest;
import dev.achiri.multivault.document.dto.DocumentResponse;
import dev.achiri.multivault.document.dto.DocumentVersionResponse;
import dev.achiri.multivault.document.service.DocumentService;
import dev.achiri.multivault.infrastructure.security.jwt.model.TenantUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    public ResponseEntity<DocumentResponse> create(
            @Valid @RequestBody CreateDocumentRequest request,
            Authentication authentication) {
        UUID ownerUserId = resolveActorUserId(request.ownerUserId(), authentication);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(documentService.create(request, ownerUserId));
    }

    @PostMapping("/{documentId}/versions")
    public ResponseEntity<DocumentVersionResponse> addVersion(
            @PathVariable UUID documentId,
            @Valid @RequestBody CreateDocumentVersionRequest request,
            Authentication authentication) {
        UUID createdBy = resolveActorUserId(request.ownerUserId(), authentication);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(documentService.addVersion(documentId, request, createdBy));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> get(@PathVariable UUID documentId) {
        return ResponseEntity.ok(documentService.get(documentId));
    }

    private UUID resolveActorUserId(UUID bodyUserId, Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof TenantUserPrincipal tenantUser) {
            return tenantUser.memberId();
        }
        if (bodyUserId == null) {
            throw new IllegalArgumentException("ownerUserId es requerido para API keys SERVICE");
        }
        return bodyUserId;
    }
}
