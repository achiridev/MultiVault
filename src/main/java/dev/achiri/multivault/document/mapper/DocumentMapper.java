package dev.achiri.multivault.document.mapper;

import dev.achiri.multivault.document.dto.DocumentResponse;
import dev.achiri.multivault.document.dto.DocumentVersionResponse;
import dev.achiri.multivault.document.model.Document;
import dev.achiri.multivault.document.model.DocumentVersion;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DocumentMapper {

    DocumentVersionResponse toVersionResponse(DocumentVersion version);

    default DocumentResponse toResponse(Document document, DocumentVersion currentVersion) {
        return new DocumentResponse(
                document.getId(),
                currentVersion == null ? null : currentVersion.getName(),
                document.getStatus(),
                currentVersion == null ? null : toVersionResponse(currentVersion));
    }
}
