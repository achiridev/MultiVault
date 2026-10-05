package dev.achiri.multivault.infrastructure.async;

import dev.achiri.multivault.tenant.provisioning.ProvisioningJob;
import dev.achiri.multivault.tenant.provisioning.ProvisioningJobQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La cola es best-effort a proposito: Redis Streams no es una base de datos y un
 * FLUSHDB pierde tanto el cache como los jobs. La fuente de verdad es el estado
 * PENDING_PROVISIONING en Postgres, del que el reconciliador reconstruye la cola.
 */
@Slf4j
@Component
public class RedisProvisioningJobQueue implements ProvisioningJobQueue {

    private final StringRedisTemplate redis;
    private final ProvisioningProperties properties;

    public RedisProvisioningJobQueue(StringRedisTemplate redis, ProvisioningProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public void enqueue(ProvisioningJob job) {
        RecordId recordId = redis.opsForStream().add(MapRecord.create(properties.streamKey(), fieldsOf(job)));
        redis.opsForStream().trim(properties.streamKey(), properties.maxStreamLength(), true);
        log.debug("Job de aprovisionamiento {} encolado como {}", job.tenantId(), recordId);
    }

    public void enqueueDeadLetter(ProvisioningJob job, String reason) {
        redis.opsForStream().add(MapRecord.create(properties.deadLetterKey(), Map.of(
                "tenantId", job.tenantId().toString(),
                "schemaName", job.schemaName(),
                "attempt", String.valueOf(job.attempt()),
                "reason", reason,
                "failedAt", String.valueOf(Instant.now().getEpochSecond()))));
        log.error("Job {} enviado a dead letter: {}", job.tenantId(), reason);
    }

    /**
     * XREADGROUP entrega mensajes nuevos a este consumidor y los deja pendientes
     * hasta el ACK. No se hace CLAIM sobre lo recién entregado: su tiempo idle es
     * cero, así que un CLAIM con min-idle-time lo filtraría y el mensaje quedaría
     * pendiente para siempre.
     */
    public List<ReceivedJob> readNew(long count) {
        return redis.opsForStream().read(
                        Consumer.from(properties.consumerGroup(), properties.consumerName()),
                        StreamReadOptions.empty().count(count),
                        StreamOffset.create(properties.streamKey(), ReadOffset.lastConsumed()))
                .stream()
                .map(record -> new ReceivedJob(record.getId(), toJob(record)))
                .toList();
    }

    /**
     * Recupera mensajes cuyo worker murió antes del ACK. XREADGROUP con '>' solo
     * entrega lo nuevo, así que sin este reclamo los tenants atascados nunca se
     * reprocesan.
     */
    public List<ReceivedJob> reclaimStalled(long count) {
        PendingMessages stalled = redis.opsForStream().pending(properties.streamKey(),
                properties.consumerGroup(), Range.unbounded(), count, properties.visibilityTimeout());
        if (stalled == null || stalled.isEmpty()) {
            return List.of();
        }
        RecordId[] ids = stalled.stream()
                .map(org.springframework.data.redis.connection.stream.PendingMessage::getId)
                .toArray(RecordId[]::new);
        return redis.opsForStream()
                .claim(properties.streamKey(), properties.consumerGroup(), properties.consumerName(),
                        properties.visibilityTimeout(), ids)
                .stream()
                .map(record -> new ReceivedJob(record.getId(), toJob(record)))
                .toList();
    }

    public void acknowledge(List<RecordId> ids) {
        if (ids.isEmpty()) {
            return;
        }
        Long acknowledged = redis.opsForStream()
                .acknowledge(properties.streamKey(), properties.consumerGroup(), ids.toArray(new RecordId[0]));
        log.debug("Ack de {} mensajes de aprovisionamiento", acknowledged);
    }

    public void acknowledge(RecordId id) {
        acknowledge(List.of(id));
    }

    public void ensureConsumerGroup() {
        try {
            redis.opsForStream().createGroup(properties.streamKey(), ReadOffset.latest(), properties.consumerGroup());
        } catch (RedisSystemException e) {
            log.debug("El grupo de consumidores {} ya existe", properties.consumerGroup());
        }
    }

    @Override
    public long pendingCount() {
        var pending = redis.opsForStream().pending(properties.streamKey(), properties.consumerGroup());
        return pending == null ? 0 : pending.getTotalPendingMessages();
    }

    private Map<String, String> fieldsOf(ProvisioningJob job) {
        return Map.of(
                "tenantId", job.tenantId().toString(),
                "schemaName", job.schemaName(),
                "planId", job.planId().toString(),
                "attempt", String.valueOf(job.attempt()),
                "enqueuedAt", String.valueOf(job.enqueuedAt().getEpochSecond()));
    }

    private ProvisioningJob toJob(MapRecord<String, Object, Object> record) {
        Map<Object, Object> values = record.getValue();
        return new ProvisioningJob(
                UUID.fromString(text(values, "tenantId")),
                text(values, "schemaName"),
                UUID.fromString(text(values, "planId")),
                Integer.parseInt(text(values, "attempt")),
                Instant.ofEpochSecond(Long.parseLong(text(values, "enqueuedAt"))));
    }

    private static String text(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        return value == null ? null : value.toString();
    }

    public record ReceivedJob(RecordId recordId, ProvisioningJob job) {
    }
}