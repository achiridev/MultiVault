package dev.achiri.multivault.tenant.provisioning;

import dev.achiri.multivault.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningFailureHandler {

    static final String FAILURE_REASON = "schema_provisioning_failed";

    private final TenantProvisioningService provisioningService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TenantRepository tenantRepository;

    @Transactional
    public void onExhausted(ProvisioningJob job) {
        if (tenantRepository.findById(job.tenantId()).isEmpty()) {
            log.warn("El tenant {} ya no existe; se descarta el job fallido", job.tenantId());
            return;
        }
        provisioningService.markProvisioningFailed(job.tenantId(), FAILURE_REASON);
        applicationEventPublisher.publishEvent(
                new TenantProvisioningFailedEvent(job.tenantId(), job.schemaName(), FAILURE_REASON));
        log.error("Aprovisionamiento del tenant {} agotó sus intentos; queda marcado como fallido",
                job.tenantId());
    }
}