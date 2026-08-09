package dev.achiri.multivault.tenant.repository;

import dev.achiri.multivault.tenant.model.TenantIdentityProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantIdentityProviderRepository extends JpaRepository<TenantIdentityProvider, UUID> {

    Optional<TenantIdentityProvider> findByIssuerAndIsActiveTrue(String issuer);

}
