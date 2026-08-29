package dev.achiri.multivault.document.controller;

import dev.achiri.multivault.audit.event.AuditContext;
import dev.achiri.multivault.document.dto.CreateDocumentRequest;
import dev.achiri.multivault.document.dto.CreateDocumentVersionRequest;
import dev.achiri.multivault.document.dto.DocumentResponse;
import dev.achiri.multivault.document.dto.DocumentVersionResponse;
import dev.achiri.multivault.document.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_documents:write')")
    public ResponseEntity<DocumentResponse> create(
            @RequestPart("file") MultipartFile file,
            @Valid CreateDocumentRequest request,
            AuditContext auditContext) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(documentService.create(file, request, auditContext));
    }

    @PostMapping(value = "/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_documents:write')")
    public ResponseEntity<DocumentVersionResponse> addVersion(
            @PathVariable UUID documentId,
            @RequestPart("file") MultipartFile file,
            @Valid CreateDocumentVersionRequest request,
            AuditContext auditContext) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(documentService.addVersion(documentId, file, request, auditContext));
    }

    @GetMapping("/{documentId}")
    @PreAuthorize("hasAuthority('SCOPE_documents:read')")
    public ResponseEntity<DocumentResponse> get(@PathVariable UUID documentId) {
        return ResponseEntity.ok(documentService.get(documentId));
    }
}
