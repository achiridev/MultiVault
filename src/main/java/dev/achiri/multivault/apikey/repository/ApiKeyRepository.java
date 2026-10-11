package dev.achiri.multivault.apikey.repository;

import dev.achiri.multivault.apikey.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHashAndRevokedAtIsNull(String keyHash);

    @Modifying
    @Query("UPDATE ApiKey a SET a.revokedAt = :revokedAt WHERE a.tenantId = :tenantId AND a.revokedAt IS NULL")
    int revokeAllByTenantId(@Param("tenantId") UUID tenantId, @Param("revokedAt") Instant revokedAt);

    List<ApiKey> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<ApiKey> findByTenantIdAndRevokedAtIsNullOrderByCreatedAtDesc(UUID tenantId);

    Optional<ApiKey> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(value = "SELECT count(*) FROM public.api_key a WHERE a.tenant_id = :t AND a.revoked_at IS NULL AND :s = ANY(a.scopes)", nativeQuery = true)
    long countActiveWithScope(@Param("t") UUID tenantId, @Param("s") String scope);

    @Query("SELECT a.keyHash FROM ApiKey a WHERE a.tenantId = :t AND a.revokedAt IS NULL")
    List<String> findActiveHashesByTenantId(@Param("t") UUID tenantId);
}
