package dev.achiri.multivault.tenant.repository;

import dev.achiri.multivault.tenant.model.TenantMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantMemberRepository extends JpaRepository<TenantMember, UUID> {

    Optional<TenantMember> findByTenantIdAndSubject(UUID tenantId, String subject);

    @Modifying
    @Query("UPDATE TenantMember m SET m.isActive = false WHERE m.tenantId = :tenantId AND m.isActive = true")
    int deactivateAllByTenantId(@Param("tenantId") UUID tenantId);
}
