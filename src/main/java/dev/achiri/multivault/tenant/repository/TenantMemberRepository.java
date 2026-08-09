package dev.achiri.multivault.tenant.repository;

import dev.achiri.multivault.tenant.model.TenantMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantMemberRepository extends JpaRepository<TenantMember, UUID> {

    Optional<TenantMember> findByTenantIdAndSubject(UUID tenantId, String subject);
}
