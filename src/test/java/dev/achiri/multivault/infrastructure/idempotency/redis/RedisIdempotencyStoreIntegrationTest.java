package dev.achiri.multivault.infrastructure.idempotency.redis;

import dev.achiri.multivault.infrastructure.idempotency.config.IdempotencyProperties;
import dev.achiri.multivault.infrastructure.idempotency.model.IdempotencyRecord;
import dev.achiri.multivault.infrastructure.idempotency.model.IdempotencyState;
import dev.achiri.multivault.infrastructure.idempotency.model.StoredResponse;
import dev.achiri.multivault.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisIdempotencyStoreIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RedisIdempotencyStore store;

    @Autowired
    private IdempotencyProperties properties;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void flush() {
        Set<String> keys = redis.keys(properties.keyPrefix() + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void claimsAKeyOnlyOnce() {
        String key = UUID.randomUUID().toString();
        IdempotencyRecord record = IdempotencyRecord.inProgress("fingerprint", Instant.now());

        assertThat(store.claim(key, record)).isTrue();
        assertThat(store.claim(key, record)).isFalse();
    }

    @Test
    void storesTheFingerprintOfTheClaimingRequest() {
        String key = UUID.randomUUID().toString();
        store.claim(key, IdempotencyRecord.inProgress("fingerprint-abc", Instant.now()));

        assertThat(store.find(key)).map(IdempotencyRecord::fingerprint).contains("fingerprint-abc");
    }

    @Test
    void findsNothingForAnUnknownKey() {
        assertThat(store.find(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void findsNothingAfterRelease() {
        String key = UUID.randomUUID().toString();
        store.claim(key, IdempotencyRecord.inProgress("fingerprint", Instant.now()));

        store.release(key);

        assertThat(store.find(key)).isEmpty();
    }

    @Test
    void roundTripsACompletedResponse() {
        String key = UUID.randomUUID().toString();
        StoredResponse response = new StoredResponse(201,
                "{\"id\":\"abc\"}".getBytes(StandardCharsets.UTF_8), "application/json");

        store.claim(key, IdempotencyRecord.inProgress("fingerprint", Instant.now()));
        store.store(key, IdempotencyRecord.completed("fingerprint", response, Instant.now()));

        IdempotencyRecord found = store.find(key).orElseThrow();
        assertThat(found.state()).isEqualTo(IdempotencyState.COMPLETED);
        assertThat(found.response().status()).isEqualTo(201);
        assertThat(found.response().contentType()).isEqualTo("application/json");
        assertThat(new String(found.response().body(), StandardCharsets.UTF_8)).isEqualTo("{\"id\":\"abc\"}");
    }

    @Test
    void expiredInProgressClaimsDisappear() {
        String key = UUID.randomUUID().toString();
        store.claim(key, IdempotencyRecord.inProgress("fingerprint", Instant.now()));

        redis.delete(properties.keyPrefix() + key);

        assertThat(store.find(key)).isEmpty();
    }

    @Test
    void inProgressClaimExpiresAfterTheConfiguredTtl() {
        String key = UUID.randomUUID().toString();
        store.claim(key, IdempotencyRecord.inProgress("fingerprint", Instant.now()));

        Long ttl = redis.getExpire(properties.keyPrefix() + key);

        assertThat(ttl).isNotNull().isPositive();
        assertThat(ttl).isLessThanOrEqualTo(properties.inProgressTtl().toSeconds());
    }

    @Test
    void completedRecordExpiresAfterTheConfiguredTtl() {
        String key = UUID.randomUUID().toString();
        store.claim(key, IdempotencyRecord.inProgress("fingerprint", Instant.now()));
        store.store(key, IdempotencyRecord.completed("fingerprint",
                new StoredResponse(200, new byte[0], "application/json"), Instant.now()));

        Long ttl = redis.getExpire(properties.keyPrefix() + key);

        assertThat(ttl).isNotNull().isPositive();
        assertThat(ttl).isLessThanOrEqualTo(properties.completedTtl().toSeconds());
    }

    @Test
    void completedTtlIsLongerThanInProgressTtl() {
        assertThat(properties.completedTtl()).isGreaterThan(properties.inProgressTtl());
    }

    @Test
    void survivesTheQueueGoingThroughRestartedConnections() {
        String key = UUID.randomUUID().toString();
        store.claim(key, IdempotencyRecord.inProgress("fingerprint", Instant.now()));
        store.store(key, IdempotencyRecord.completed("fingerprint",
                new StoredResponse(201, "{\"ok\":true}".getBytes(StandardCharsets.UTF_8), "application/json"),
                Instant.now()));

        assertThat(store.find(key)).isPresent();
        assertThat(redis.opsForValue().get(properties.keyPrefix() + key)).contains("COMPLETED");
    }

    @Test
    void handlesAnEmptyResponseBody() {
        String key = UUID.randomUUID().toString();
        store.claim(key, IdempotencyRecord.inProgress("fingerprint", Instant.now()));
        store.store(key, IdempotencyRecord.completed("fingerprint",
                new StoredResponse(204, new byte[0], null), Instant.now()));

        IdempotencyRecord found = store.find(key).orElseThrow();
        assertThat(found.response().body()).isEmpty();
        assertThat(found.response().status()).isEqualTo(204);
    }

    @Test
    void treatsAnUnreadablePayloadAsAbsent() {
        String key = UUID.randomUUID().toString();
        redis.opsForValue().set(properties.keyPrefix() + key, "not-json", Duration.ofMinutes(1));

        assertThat(store.find(key)).isEmpty();
    }
}