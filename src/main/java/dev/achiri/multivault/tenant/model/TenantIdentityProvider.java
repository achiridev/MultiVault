package dev.achiri.multivault.tenant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tenant_identity_provider")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class TenantIdentityProvider {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "issuer", nullable = false, length = 255)
    private String issuer;

    @Column(name = "jwks_uri", nullable = false, length = 500)
    private String jwksUri;

    @Column(name = "audience", nullable = false, length = 255)
    private String audience;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Array(length = 100)
    @Column(name = "allowed_algorithms", nullable = false)
    private List<String> allowedAlgorithms = List.of("RS256");

    @Column(name = "clock_skew_seconds", nullable = false)
    private Integer clockSkewSeconds = 60;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
