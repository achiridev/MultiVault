package dev.achiri.multivault.infrastructure.idempotency.redis;

import dev.achiri.multivault.infrastructure.idempotency.config.IdempotencyProperties;
import dev.achiri.multivault.infrastructure.idempotency.model.IdempotencyRecord;
import dev.achiri.multivault.infrastructure.idempotency.model.IdempotencyState;
import dev.achiri.multivault.infrastructure.idempotency.model.StoredResponse;
import dev.achiri.multivault.infrastructure.idempotency.spi.IdempotencyStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
public class RedisIdempotencyStore implements IdempotencyStore {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final IdempotencyProperties properties;

    public RedisIdempotencyStore(StringRedisTemplate redis, ObjectMapper objectMapper,
                                 IdempotencyProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public boolean claim(String key, IdempotencyRecord record) {
        String payload = serialize(record);
        Boolean claimed = redis.opsForValue().setIfAbsent(storageKey(key), payload,
                properties.inProgressTtl());
        return Boolean.TRUE.equals(claimed);
    }

    @Override
    public Optional<IdempotencyRecord> find(String key) {
        return Optional.ofNullable(redis.opsForValue().get(storageKey(key))).flatMap(this::deserialize);
    }

    @Override
    public void store(String key, IdempotencyRecord record) {
        redis.opsForValue().set(storageKey(key), serialize(record), properties.completedTtl());
    }

    @Override
    public void release(String key) {
        redis.delete(storageKey(key));
    }

    private String serialize(IdempotencyRecord record) {
        IdempotencyEntry entry = new IdempotencyEntry(
                record.state().name(),
                record.fingerprint(),
                record.createdAt().getEpochSecond(),
                record.response() == null ? null : record.response().status(),
                record.response() == null ? null : record.response().contentType(),
                record.response() == null ? null : record.response().body());
        return objectMapper.writeValueAsString(entry);
    }

    private Optional<IdempotencyRecord> deserialize(String payload) {
        IdempotencyEntry entry;
        try {
            entry = objectMapper.readValue(payload, IdempotencyEntry.class);
        } catch (RuntimeException e) {
            log.warn("Registro de idempotencia ilegible; se trata como ausente", e);
            return Optional.empty();
        }
        Instant createdAt = Instant.ofEpochSecond(entry.createdAtEpochSecond());
        if (IdempotencyState.COMPLETED.name().equals(entry.state())) {
            StoredResponse response = new StoredResponse(
                    entry.status() == null ? 200 : entry.status(),
                    entry.body() == null ? new byte[0] : entry.body(),
                    entry.contentType());
            return Optional.of(IdempotencyRecord.completed(entry.fingerprint(), response, createdAt));
        }
        return Optional.of(IdempotencyRecord.inProgress(entry.fingerprint(), createdAt));
    }

    private String storageKey(String key) {
        return properties.keyPrefix() + key;
    }
}