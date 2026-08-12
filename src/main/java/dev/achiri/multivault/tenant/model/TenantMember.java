package dev.achiri.multivault.tenant.model;

import dev.achiri.multivault.infrastructure.persistence.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_member", schema = "public")
@Getter
@Setter
public class TenantMember extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt = Instant.now();

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();
}
