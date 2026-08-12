package dev.achiri.multivault.tenant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_usage", schema = "public")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class TenantUsage {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "storage_bytes_used", nullable = false)
    private Long storageBytesUsed = 0L;

    @Column(name = "user_count", nullable = false)
    private Integer userCount = 0;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
