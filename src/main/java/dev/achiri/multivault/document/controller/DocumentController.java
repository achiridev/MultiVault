package dev.achiri.multivault.document.controller;

import dev.achiri.multivault.audit.event.AuditContext;
import dev.achiri.multivault.audit.event.AuditContextResolver;
import dev.achiri.multivault.document.dto.DocumentResponse;
import dev.achiri.multivault.document.dto.DocumentVersionResponse;
import dev.achiri.multivault.document.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final AuditContextResolver auditContextResolver;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> create(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String mimeType,
            @RequestParam(required = false) UUID folderId,
            @RequestParam(required = false) UUID ownerUserId,
            Authentication authentication,
            HttpServletRequest httpRequest) throws IOException {
        AuditContext auditContext = auditContextResolver.resolve(ownerUserId, authentication, httpRequest);
        String fileName = (name != null && !name.isBlank()) ? name : file.getOriginalFilename();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(documentService.create(file, fileName, mimeType, folderId, auditContext));
    }

    @PostMapping(value = "/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentVersionResponse> addVersion(
            @PathVariable UUID documentId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String mimeType,
            @RequestParam(required = false) UUID ownerUserId,
            Authentication authentication,
            HttpServletRequest httpRequest) throws IOException {
        AuditContext auditContext = auditContextResolver.resolve(ownerUserId, authentication, httpRequest);
        String fileName = (name != null && !name.isBlank()) ? name : file.getOriginalFilename();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(documentService.addVersion(documentId, file, fileName, mimeType, auditContext));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> get(@PathVariable UUID documentId) {
        return ResponseEntity.ok(documentService.get(documentId));
    }
}
