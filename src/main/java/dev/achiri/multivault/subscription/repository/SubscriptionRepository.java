package dev.achiri.multivault.subscription.repository;

import dev.achiri.multivault.subscription.model.Subscription;
import dev.achiri.multivault.subscription.model.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByTenantIdAndStatus(UUID tenantId, SubscriptionStatus status);
}
