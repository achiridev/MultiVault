package dev.achiri.multivault.tenant.provisioning;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ProvisioningRequestedListener {

    private final ProvisioningJobQueue jobQueue;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ProvisioningRequestedEvent event) {
        jobQueue.enqueue(new ProvisioningJob(
                event.tenantId(), event.schemaName(), event.planId(), 0, Instant.now()));
    }
}