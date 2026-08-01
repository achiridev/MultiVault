package dev.achiri.multivault.infrastructure.persistence.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@MappedSuperclass
@Getter
@Setter
public abstract class SoftDeletable extends DateAudit {
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
