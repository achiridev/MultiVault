package dev.achiri.multivault.tenant.service;

import dev.achiri.multivault.apikey.repository.ApiKeyRepository;
import dev.achiri.multivault.audit.event.AuditEvent;
import dev.achiri.multivault.audit.event.AuditEventPublisher;
import dev.achiri.multivault.audit.model.ActorType;
import dev.achiri.multivault.common.exception.EstadoTransicionInvalidoException;
import dev.achiri.multivault.common.exception.RecursoNoEncontradoException;
import dev.achiri.multivault.subscription.model.Subscription;
import dev.achiri.multivault.subscription.model.SubscriptionStatus;
import dev.achiri.multivault.subscription.repository.SubscriptionRepository;
import dev.achiri.multivault.tenant.dto.TenantStatusResponse;
import dev.achiri.multivault.tenant.dto.UpdateTenantStatusRequest;
import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.model.TenantStatus;
import dev.achiri.multivault.tenant.repository.TenantMemberRepository;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantLifecycleService {

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final AuditEventPublisher auditEventPublisher;

    @Transactional
    public TenantStatusResponse updateStatus(UUID tenantId, UpdateTenantStatusRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("tenant", tenantId));

        TenantStatus previousStatus = tenant.getStatus();
        TenantStatus requestedStatus = request.status();

        if (previousStatus == requestedStatus) {
            return toStatusResponse(tenant, previousStatus);
        }

        validateTransition(previousStatus, requestedStatus);

        switch (requestedStatus) {
            case CANCELLED -> cancelTenant(tenant, request.reason());
            case SUSPENDED -> suspendTenant(tenant, request.reason());
            case ACTIVE -> reinstateTenant(tenant);
            default -> throw new EstadoTransicionInvalidoException(previousStatus, requestedStatus);
        }

        tenantRepository.save(tenant);

        return toStatusResponse(tenant, previousStatus);
    }

    private void validateTransition(TenantStatus from, TenantStatus to) {
        boolean valid = switch (from) {
            case PENDING_PROVISIONING -> to == TenantStatus.CANCELLED || to == TenantStatus.SUSPENDED;
            case ACTIVE -> to == TenantStatus.CANCELLED || to == TenantStatus.SUSPENDED;
            case SUSPENDED -> to == TenantStatus.ACTIVE || to == TenantStatus.CANCELLED;
            case CANCELLED -> false;
        };

        if (!valid) {
            throw new EstadoTransicionInvalidoException(from, to);
        }
    }

    private void cancelTenant(Tenant tenant, String reason) {
        tenant.setStatus(TenantStatus.CANCELLED);

        cancelSubscription(tenant.getId());
        revokeAllApiKeys(tenant.getId());
        deactivateAllMembers(tenant.getId());

        auditEventPublisher.publish(AuditEvent.builder()
                .tenantId(tenant.getId())
                .actorType(ActorType.SYSTEM)
                .action("TENANT_CANCELLED")
                .resourceType("tenant")
                .resourceId(tenant.getId())
                .metadata(Map.of(
                        "tenant_name", tenant.getName(),
                        "reason", reason != null ? reason : "user_requested"))
                .build());
    }

    private void suspendTenant(Tenant tenant, String reason) {
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenant.setSuspendedAt(Instant.now());
        tenant.setSuspendedReason(reason);

        suspendSubscription(tenant.getId());
        revokeAllApiKeys(tenant.getId());
        deactivateAllMembers(tenant.getId());

        auditEventPublisher.publish(AuditEvent.builder()
                .tenantId(tenant.getId())
                .actorType(ActorType.SYSTEM)
                .action("TENANT_SUSPENDED")
                .resourceType("tenant")
                .resourceId(tenant.getId())
                .metadata(Map.of(
                        "tenant_name", tenant.getName(),
                        "reason", reason != null ? reason : "user_requested"))
                .build());
    }

    private void reinstateTenant(Tenant tenant) {
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setSuspendedAt(null);
        tenant.setSuspendedReason(null);

        reinstateSubscription(tenant.getId());

        auditEventPublisher.publish(AuditEvent.builder()
                .tenantId(tenant.getId())
                .actorType(ActorType.SYSTEM)
                .action("TENANT_REINSTATED")
                .resourceType("tenant")
                .resourceId(tenant.getId())
                .metadata(Map.of("tenant_name", tenant.getName()))
                .build());
    }

    private void cancelSubscription(UUID tenantId) {
        subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE)
                .ifPresent(subscription -> {
                    subscription.setStatus(SubscriptionStatus.CANCELLED);
                    subscription.setCancelledAt(Instant.now());
                    subscriptionRepository.save(subscription);
                });
    }

    private void suspendSubscription(UUID tenantId) {
        subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE)
                .ifPresent(subscription -> {
                    subscription.setStatus(SubscriptionStatus.PAST_DUE);
                    subscriptionRepository.save(subscription);
                });
    }

    private void reinstateSubscription(UUID tenantId) {
        subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.PAST_DUE)
                .ifPresent(subscription -> {
                    subscription.setStatus(SubscriptionStatus.ACTIVE);
                    subscriptionRepository.save(subscription);
                });
    }

    private void revokeAllApiKeys(UUID tenantId) {
        apiKeyRepository.revokeAllByTenantId(tenantId, Instant.now());
    }

    private void deactivateAllMembers(UUID tenantId) {
        tenantMemberRepository.deactivateAllByTenantId(tenantId);
    }

    private TenantStatusResponse toStatusResponse(Tenant tenant, TenantStatus previousStatus) {
        return new TenantStatusResponse(
                tenant.getId(),
                tenant.getName(),
                previousStatus,
                tenant.getStatus(),
                tenant.getSuspendedAt(),
                tenant.getSuspendedReason());
    }
}
