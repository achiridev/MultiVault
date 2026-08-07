package dev.achiri.multivault.tenant.provisioning;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantProvisioningFailureListener {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningFailureListener.class);

    @EventListener
    public void on(TenantProvisioningFailedEvent event) {
        log.error("Tenant {} (schema {}) provisioning failed: {}",
                event.tenantId(), event.schemaName(), event.reason());
    }
}
