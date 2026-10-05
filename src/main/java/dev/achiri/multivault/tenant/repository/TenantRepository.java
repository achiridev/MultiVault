package dev.achiri.multivault.tenant.repository;

import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.model.TenantStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    @Modifying
    @Query("""
            UPDATE Tenant t
               SET t.status = :active, t.currentPlanId = :planId
             WHERE t.id = :id AND t.status = :pending
            """)
    int activateIfPending(@Param("id") UUID id,
                          @Param("planId") UUID planId,
                          @Param("pending") TenantStatus pending,
                          @Param("active") TenantStatus active);

    List<Tenant> findByStatusAndUpdatedAtBefore(TenantStatus status, Instant cutoff, Sort sort);
}