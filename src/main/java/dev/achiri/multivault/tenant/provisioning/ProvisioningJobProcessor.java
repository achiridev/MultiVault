package dev.achiri.multivault.tenant.provisioning;

import dev.achiri.multivault.infrastructure.persistence.tenant.TenantSchemaProvisioner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProvisioningJobProcessor {

    private final TenantSchemaProvisioner tenantSchemaProvisioner;
    private final TenantProvisioningService provisioningService;

    public void process(ProvisioningJob job) {
        tenantSchemaProvisioner.provision(job.schemaName());
        if (provisioningService.activateIfPending(job.tenantId(), job.planId())) {
            log.info("Tenant {} activado tras aprovisionar el schema {}", job.tenantId(), job.schemaName());
            return;
        }
        log.warn("Tenant {} ya no estaba en PENDING_PROVISIONING; no se activa de nuevo", job.tenantId());
    }
}