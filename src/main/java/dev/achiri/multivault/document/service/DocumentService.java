package dev.achiri.multivault.document.service;

import dev.achiri.multivault.audit.event.AuditContext;
import dev.achiri.multivault.audit.event.AuditEvent;
import dev.achiri.multivault.audit.event.AuditEventPublisher;
import dev.achiri.multivault.common.exception.RecursoNoEncontradoException;
import dev.achiri.multivault.common.exception.StorageException;
import dev.achiri.multivault.document.dto.CreateDocumentRequest;
import dev.achiri.multivault.document.dto.CreateDocumentVersionRequest;
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
import dev.achiri.multivault.tenant.usage.StorageQuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
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
    private final UploadPolicy uploadPolicy;
    private final StorageQuotaService storageQuotaService;
    private final TransactionTemplate transactionTemplate;

    public DocumentResponse create(MultipartFile file, CreateDocumentRequest request, AuditContext auditContext) {
        uploadPolicy.validate(file);
        String name = resolveName(request.name(), file);
        String mimeType = resolveMimeType(request.mimeType(), file);
        uploadPolicy.validateMimeType(mimeType);

        String checksum = checksumOf(file);
        long sizeBytes = file.getSize();
        storageQuotaService.assertCapacity(auditContext.tenantId(), sizeBytes);

        Document document = transactionTemplate.execute(tx -> {
            Document fresh = new Document();
            fresh.setFolderId(request.folderId());
            fresh.setOwnerUserId(auditContext.actorUserId());
            fresh.setStatus(DocumentStatus.ACTIVE);
            return documentRepository.save(fresh);
        });

        String storageKey = storageKey(document.getId(), 1, checksum);

        try {
            upload(storageKey, file, mimeType, sizeBytes);
        } catch (RuntimeException e) {
            deleteDocumentQuietly(document.getId());
            throw e;
        }

        try {
            return transactionTemplate.execute(tx -> {
                DocumentVersion version = saveVersion(document.getId(), auditContext.actorUserId(), 1,
                        name, storageKey, mimeType, sizeBytes, checksum);
                document.setCurrentVersionId(version.getId());
                documentRepository.save(document);

                storageQuotaService.addStorageBytes(auditContext.tenantId(), sizeBytes);

                publishAudit(auditContext, "DOCUMENT_CREATED", "document", document.getId(),
                        Map.of("document_name", name, "version_number", 1));

                return documentMapper.toResponse(document, version);
            });
        } catch (RuntimeException e) {
            deleteObjectQuietly(storageKey);
            deleteDocumentQuietly(document.getId());
            throw e;
        }
    }

    public DocumentVersionResponse addVersion(UUID documentId, MultipartFile file,
                                              CreateDocumentVersionRequest request, AuditContext auditContext) {
        uploadPolicy.validate(file);
        requireDocument(documentId);

        int versionNumber = nextVersionNumber(documentId);
        String name = resolveName(request.name(), file);
        String mimeType = resolveMimeType(request.mimeType(), file);
        uploadPolicy.validateMimeType(mimeType);

        String checksum = checksumOf(file);
        long sizeBytes = file.getSize();
        storageQuotaService.assertCapacity(auditContext.tenantId(), sizeBytes);
        String storageKey = storageKey(documentId, versionNumber, checksum);

        upload(storageKey, file, mimeType, sizeBytes);

        try {
            return transactionTemplate.execute(tx -> {
                Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                        .orElseThrow(() -> new RecursoNoEncontradoException("document", documentId));
                DocumentVersion version = saveVersion(documentId, auditContext.actorUserId(), versionNumber,
                        name, storageKey, mimeType, sizeBytes, checksum);
                document.setCurrentVersionId(version.getId());

                storageQuotaService.addStorageBytes(auditContext.tenantId(), sizeBytes);

                publishAudit(auditContext, "DOCUMENT_VERSION_UPLOADED", "document_version", version.getId(),
                        Map.of(
                                "document_id", documentId.toString(),
                                "document_name", name,
                                "version_number", versionNumber));

                return documentMapper.toVersionResponse(version);
            });
        } catch (RuntimeException e) {
            deleteObjectQuietly(storageKey);
            throw e;
        }
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

    private int nextVersionNumber(UUID documentId) {
        return documentVersionRepository
                .findTopByDocumentIdOrderByVersionNumberDesc(documentId)
                .map(version -> version.getVersionNumber() + 1)
                .orElse(1);
    }

    private void requireDocument(UUID documentId) {
        documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new RecursoNoEncontradoException("document", documentId));
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

    private void publishAudit(AuditContext auditContext, String action, String resourceType,
                              UUID resourceId, Map<String, Object> metadata) {
        auditEventPublisher.publish(AuditEvent.builder()
                .tenantId(auditContext.tenantId())
                .actorUserId(auditContext.actorUserId())
                .apiKeyId(auditContext.apiKeyId())
                .actorType(auditContext.actorType())
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .ipAddress(auditContext.ipAddress())
                .userAgent(auditContext.userAgent())
                .metadata(metadata)
                .build());
    }

    private String storageKey(UUID documentId, int versionNumber, String checksum) {
        return TenantContext.getSchema() + "/" + documentId + "/" + versionNumber + "/" + checksum;
    }

    private String checksumOf(MultipartFile file) {
        try {
            return DocumentHashUtil.sha256Hex(file.getInputStream());
        } catch (IOException e) {
            throw new StorageException("No se pudo leer el archivo", e);
        }
    }

    private void upload(String storageKey, MultipartFile file, String mimeType, long sizeBytes) {
        try {
            InputStream content = file.getInputStream();
            documentStorageService.upload(storageKey, content, mimeType, sizeBytes);
        } catch (IOException e) {
            throw new StorageException("No se pudo subir el archivo al almacenamiento", e);
        }
    }

    private void deleteObjectQuietly(String storageKey) {
        try {
            documentStorageService.delete(storageKey);
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private void deleteDocumentQuietly(UUID documentId) {
        try {
            documentRepository.deleteById(documentId);
        } catch (RuntimeException ignored) {
        }
    }

    private String resolveName(String requested, MultipartFile file) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        String original = file.getOriginalFilename();
        if (original != null && !original.isBlank()) {
            int lastSeparator = Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\'));
            return original.substring(lastSeparator + 1);
        }
        throw new StorageException("El archivo no tiene nombre");
    }

    private String resolveMimeType(String requested, MultipartFile file) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        String contentType = file.getContentType();
        return (contentType != null && !contentType.isBlank()) ? contentType : "application/octet-stream";
    }
}
