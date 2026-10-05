package dev.achiri.multivault.tenant.provisioning;

import dev.achiri.multivault.infrastructure.async.ProvisioningProperties;
import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.model.TenantStatus;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Redis Streams no es una base de datos: un FLUSHDB o una perdida de la instancia
 * deja tenants en PENDING_PROVISIONING sin job encolado. Postgres es la fuente de
 * verdad y este reconciliador reconstruye la cola a partir de el.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProvisioningReconciler {

    private static final int BATCH_LIMIT = 100;

    private final TenantRepository tenantRepository;
    private final ProvisioningJobQueue jobQueue;
    private final ProvisioningProperties properties;

    @Scheduled(fixedDelayString = "${multivault.provisioning.reconcile-interval:PT2M}")
    public void reconcile() {
        if (!properties.enabled()) {
            return;
        }
        List<Tenant> stuck = findStuckTenants();
        if (stuck.isEmpty()) {
            return;
        }
        log.warn("{} tenants atascados en PENDING_PROVISIONING; reencolando su aprovisionamiento", stuck.size());
        stuck.forEach(this::requeue);
    }

    @Transactional(readOnly = true)
    List<Tenant> findStuckTenants() {
        return tenantRepository.findByStatusAndUpdatedAtBefore(
                TenantStatus.PENDING_PROVISIONING, Instant.now().minus(properties.staleAfter()),
                Sort.by(Sort.Direction.ASC, "createdAt"));
    }

    private void requeue(Tenant tenant) {
        if (tenant.getCurrentPlanId() == null) {
            log.error("Tenant {} lleva mucho tiempo en PENDING_PROVISIONING sin plan asignado; "
                    + "no se puede reencolar", tenant.getId());
            return;
        }
        jobQueue.enqueue(new ProvisioningJob(tenant.getId(), tenant.getSchemaName(),
                tenant.getCurrentPlanId(), 0, Instant.now()));
    }
}