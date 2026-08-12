package dev.achiri.multivault.document.service;

import dev.achiri.multivault.common.exception.RecursoNoEncontradoException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentMapper documentMapper;

    @Transactional
    public DocumentResponse create(CreateDocumentRequest request, UUID ownerUserId) {
        Document document = new Document();
        document.setFolderId(request.folderId());
        document.setOwnerUserId(ownerUserId);
        document.setStatus(DocumentStatus.ACTIVE);
        document = documentRepository.save(document);

        DocumentVersion version = saveVersion(
                document.getId(), ownerUserId, 1, request.name(), request.mimeType(), request.sizeBytes(),
                request.checksum());
        document.setCurrentVersionId(version.getId());
        documentRepository.save(document);

        return documentMapper.toResponse(document, version);
    }

    @Transactional
    public DocumentVersionResponse addVersion(UUID documentId, CreateDocumentVersionRequest request, UUID createdBy) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new RecursoNoEncontradoException("document", documentId));

        int versionNumber = documentVersionRepository
                .findTopByDocumentIdOrderByVersionNumberDesc(documentId)
                .map(version -> version.getVersionNumber() + 1)
                .orElse(1);

        DocumentVersion version = saveVersion(
                documentId, createdBy, versionNumber, request.name(), request.mimeType(), request.sizeBytes(),
                request.checksum());
        document.setCurrentVersionId(version.getId());
        documentRepository.save(document);

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
                                        String mimeType, Long sizeBytes, String checksum) {
        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(documentId);
        version.setVersionNumber(versionNumber);
        version.setName(name);
        version.setStorageKey(TenantContext.getSchema() + "/" + documentId + "/" + versionNumber + "/" + checksum);
        version.setMimeType(mimeType);
        version.setSizeBytes(sizeBytes);
        version.setChecksum(checksum);
        version.setCreatedBy(createdBy);
        return documentVersionRepository.save(version);
    }
}
