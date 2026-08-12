package dev.achiri.multivault.plan.model;

import dev.achiri.multivault.infrastructure.persistence.base.DateAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "plan", schema = "public")
@Getter
@Setter
public class Plan extends DateAudit {

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private PlanCode code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "price_cents", nullable = false)
    private Integer priceCents;

    @Column(name = "max_storage_bytes", nullable = false)
    private Long maxStorageBytes;

    @Column(name = "max_users", nullable = false)
    private Integer maxUsers;

    @Column(name = "max_requests_per_minute", nullable = false)
    private Integer maxRequestsPerMinute;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
