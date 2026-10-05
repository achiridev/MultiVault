package dev.achiri.multivault.infrastructure.idempotency.web;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyKeyValidatorTest {

    @Test
    void acceptsAUuid() {
        assertThat(IdempotencyKeyValidator.invalidReason(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void acceptsAUuidSurroundedByWhitespace() {
        assertThat(IdempotencyKeyValidator.invalidReason("  " + UUID.randomUUID() + "  ")).isEmpty();
    }

    @Test
    void rejectsAMissingKey() {
        assertThat(IdempotencyKeyValidator.invalidReason(null)).isPresent();
    }

    @Test
    void rejectsABlankKey() {
        assertThat(IdempotencyKeyValidator.invalidReason("   ")).isPresent();
    }

    @Test
    void rejectsANonUuidKey() {
        assertThat(IdempotencyKeyValidator.invalidReason("not-a-uuid")).isPresent();
    }

    @Test
    void rejectsAKeyLongerThanTheLimit() {
        assertThat(IdempotencyKeyValidator.invalidReason("a".repeat(65))).isPresent();
    }

    @Test
    void rejectsAKeyContainingShellMetacharacters() {
        assertThat(IdempotencyKeyValidator.invalidReason("../../etc/passwd")).isPresent();
    }

    @Test
    void rejectsAKeyWithRedisKeySeparators() {
        assertThat(IdempotencyKeyValidator.invalidReason("abc:def")).isPresent();
    }
}