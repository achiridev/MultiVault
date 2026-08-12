package dev.achiri.multivault.document.model;

import dev.achiri.multivault.infrastructure.persistence.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_version")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class DocumentVersion extends BaseEntity {

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Column(name = "storage_key", nullable = false, length = 1000)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = 150)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "checksum", nullable = false, length = 128)
    private String checksum;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    private JsonNode metadata = JsonNodeFactory.instance.objectNode();

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "created_by_snapshot")
    private JsonNode createdBySnapshot;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
