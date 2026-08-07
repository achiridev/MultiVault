package dev.achiri.multivault.tenant.repository;

import dev.achiri.multivault.tenant.model.TenantUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TenantUsageRepository extends JpaRepository<TenantUsage, UUID> {

}
