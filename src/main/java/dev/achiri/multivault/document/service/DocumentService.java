package dev.achiri.multivault.document.service;

import dev.achiri.multivault.audit.event.AuditContext;
import dev.achiri.multivault.audit.event.AuditEvent;
import dev.achiri.multivault.audit.event.AuditEventPublisher;
import dev.achiri.multivault.common.exception.RecursoNoEncontradoException;
import dev.achiri.multivault.document.dto.DocumentResponse;
import dev.achiri.multivault.document.dto.DocumentVersionResponse;
import dev.achiri.multivault.document.mapper.DocumentMapper;
import dev.achiri.multivault.document.model.Document;
import dev.achiri.multivault.document.model.DocumentStatus;
import dev.achiri.multivault.document.model.DocumentVersion;
import dev.achiri.multivault.document.repository.DocumentRepository;
import dev.achiri.multivault.document.repository.DocumentVersionRepository;
import dev.achiri.multivault.infrastructure.persistence.tenant.context.TenantContext;
import dev.achiri.multivault.infrastructure.storage.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentMapper documentMapper;
    private final AuditEventPublisher auditEventPublisher;
    private final DocumentStorageService documentStorageService;

    @Transactional
    public DocumentResponse create(MultipartFile file, String name, String mimeType,
                                   UUID folderId, AuditContext auditContext) throws IOException {
        byte[] fileBytes = file.getBytes();
        String checksum = DocumentHashUtil.sha256Hex(fileBytes);
        long sizeBytes = fileBytes.length;
        String resolvedMimeType = (mimeType != null && !mimeType.isBlank()) ? mimeType : file.getContentType();

        Document document = new Document();
        document.setFolderId(folderId);
        document.setOwnerUserId(auditContext.actorUserId());
        document.setStatus(DocumentStatus.ACTIVE);
        document = documentRepository.save(document);

        String storageKey = TenantContext.getSchema() + "/" + document.getId() + "/1/" + checksum;

        documentStorageService.upload(
                storageKey,
                new ByteArrayInputStream(fileBytes),
                resolvedMimeType,
                sizeBytes);

        DocumentVersion version = saveVersion(
                document.getId(), auditContext.actorUserId(), 1, name, storageKey,
                resolvedMimeType, sizeBytes, checksum);
        document.setCurrentVersionId(version.getId());
        documentRepository.save(document);

        auditEventPublisher.publish(AuditEvent.builder()
                .tenantId(auditContext.tenantId())
                .actorUserId(auditContext.actorUserId())
                .apiKeyId(auditContext.apiKeyId())
                .actorType(auditContext.actorType())
                .action("DOCUMENT_CREATED")
                .resourceType("document")
                .resourceId(document.getId())
                .ipAddress(auditContext.ipAddress())
                .userAgent(auditContext.userAgent())
                .metadata(Map.of("document_name", name, "version_number", 1))
                .build());

        return documentMapper.toResponse(document, version);
    }

    @Transactional
    public DocumentVersionResponse addVersion(UUID documentId, MultipartFile file, String name,
                                              String mimeType, AuditContext auditContext) throws IOException {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new RecursoNoEncontradoException("document", documentId));

        int versionNumber = documentVersionRepository
                .findTopByDocumentIdOrderByVersionNumberDesc(documentId)
                .map(version -> version.getVersionNumber() + 1)
                .orElse(1);

        byte[] fileBytes = file.getBytes();
        String checksum = DocumentHashUtil.sha256Hex(fileBytes);
        long sizeBytes = fileBytes.length;
        String resolvedMimeType = (mimeType != null && !mimeType.isBlank()) ? mimeType : file.getContentType();

        String storageKey = TenantContext.getSchema() + "/" + documentId + "/" + versionNumber + "/" + checksum;

        documentStorageService.upload(
                storageKey,
                new ByteArrayInputStream(fileBytes),
                resolvedMimeType,
                sizeBytes);

        DocumentVersion version = saveVersion(
                documentId, auditContext.actorUserId(), versionNumber, name, storageKey,
                resolvedMimeType, sizeBytes, checksum);
        document.setCurrentVersionId(version.getId());
        documentRepository.save(document);

        auditEventPublisher.publish(AuditEvent.builder()
                .tenantId(auditContext.tenantId())
                .actorUserId(auditContext.actorUserId())
                .apiKeyId(auditContext.apiKeyId())
                .actorType(auditContext.actorType())
                .action("DOCUMENT_VERSION_UPLOADED")
                .resourceType("document_version")
                .resourceId(version.getId())
                .ipAddress(auditContext.ipAddress())
                .userAgent(auditContext.userAgent())
                .metadata(Map.of(
                        "document_id", documentId.toString(),
                        "document_name", name,
                        "version_number", versionNumber))
                .build());

        return documentMapper.toVersionResponse(version);
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(UUID documentId) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new RecursoNoEncontradoException("document", documentId));
        DocumentVersion currentVersion = document.getCurrentVersionId() == null
                ? null
                : documentVersionRepository.findById(document.getCurrentVersionId()).orElse(null);
        return documentMapper.toResponse(document, currentVersion);
    }

    private DocumentVersion saveVersion(UUID documentId, UUID createdBy, int versionNumber, String name,
                                        String storageKey, String mimeType, long sizeBytes, String checksum) {
        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(documentId);
        version.setVersionNumber(versionNumber);
        version.setName(name);
        version.setStorageKey(storageKey);
        version.setMimeType(mimeType);
        version.setSizeBytes(sizeBytes);
        version.setChecksum(checksum);
        version.setCreatedBy(createdBy);
        return documentVersionRepository.save(version);
    }
}
