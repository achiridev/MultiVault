package dev.achiri.multivault.document.repository;

import dev.achiri.multivault.document.model.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    Optional<DocumentVersion> findTopByDocumentIdOrderByVersionNumberDesc(UUID documentId);
}
