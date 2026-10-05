package dev.achiri.multivault.infrastructure.async;

import dev.achiri.multivault.tenant.provisioning.ProvisioningJob;
import dev.achiri.multivault.tenant.provisioning.ProvisioningJobProcessor;
import dev.achiri.multivault.tenant.provisioning.TenantProvisioningFailureHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProvisioningWorker {

    private static final long POLL_INTERVAL_MILLIS = 1_000L;
    private static final int BATCH_SIZE = 8;

    private final RedisProvisioningJobQueue queue;
    private final ProvisioningJobProcessor processor;
    private final TenantProvisioningFailureHandler failureHandler;
    private final ProvisioningProperties properties;

    private volatile boolean running;
    private Thread worker;

    @PostConstruct
    void start() {
        if (!properties.enabled()) {
            log.warn("El aprovisionamiento asíncrono está deshabilitado; "
                    + "POST /api/v1/tenants aprovisiona de forma síncrona");
            return;
        }
        queue.ensureConsumerGroup();
        running = true;
        worker = new Thread(this::pollLoop, "tenant-provisioning-worker");
        worker.setDaemon(true);
        worker.start();
    }

    @PreDestroy
    void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    void drainOnce() {
        List<RedisProvisioningJobQueue.ReceivedJob> received = queue.readNew(BATCH_SIZE);
        if (received.isEmpty()) {
            received = queue.reclaimStalled(BATCH_SIZE);
        }
        List<RecordId> acknowledged = new ArrayList<>();
        for (RedisProvisioningJobQueue.ReceivedJob job : received) {
            // Un job que revienta no debe dejar sin procesar ni sin ack al resto del lote.
            try {
                process(job.job());
            } catch (RuntimeException e) {
                log.error("Fallo inesperado procesando el job de aprovisionamiento {}", job.job().tenantId(), e);
            }
            acknowledged.add(job.recordId());
        }
        queue.acknowledge(acknowledged);
    }

    private void process(ProvisioningJob job) {
        try {
            processor.process(job);
        } catch (RuntimeException e) {
            log.warn("Aprovisionamiento del tenant {} falló en el intento {}",
                    job.tenantId(), job.attempt(), e);
            if (job.attempt() + 1 >= properties.maxAttempts()) {
                failureHandler.onExhausted(job);
                queue.enqueueDeadLetter(job, e.getClass().getSimpleName());
                return;
            }
            queue.enqueue(job.nextAttempt());
        }
    }

    private void pollLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                drainOnce();
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                log.error("El worker de aprovisionamiento falló; reintentará en el siguiente ciclo", e);
                sleepQuietly();
            }
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}