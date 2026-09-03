package dev.achiri.multivault.tenant.repository;

import dev.achiri.multivault.tenant.model.TenantUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantUsageRepository extends JpaRepository<TenantUsage, UUID> {

    Optional<TenantUsage> findByTenantId(UUID tenantId);

    @Modifying
    @Query("UPDATE TenantUsage u SET u.storageBytesUsed = u.storageBytesUsed + :bytes WHERE u.tenantId = :tenantId")
    int incrementStorageBytes(@Param("tenantId") UUID tenantId, @Param("bytes") long bytes);
}
