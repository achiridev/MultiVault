package dev.achiri.multivault.infrastructure.idempotency.spi;

import dev.achiri.multivault.infrastructure.idempotency.model.IdempotencyRecord;

import java.util.Optional;

public interface IdempotencyStore {

    boolean claim(String key, IdempotencyRecord record);

    Optional<IdempotencyRecord> find(String key);

    void store(String key, IdempotencyRecord record);

    void release(String key);
}