package dev.achiri.multivault.audit.event;

import dev.achiri.multivault.audit.model.ActorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventTest {

    @Test
    void defaultsActorTypeToSystem() {
        AuditEvent event = AuditEvent.builder()
                .tenantId(UUID.randomUUID())
                .action("TEST_ACTION")
                .build();

        assertThat(event.getActorType()).isEqualTo(ActorType.SYSTEM);
    }

    @Test
    void keepsExplicitActorType() {
        AuditEvent event = AuditEvent.builder()
                .actorType(ActorType.API_KEY)
                .action("TEST_ACTION")
                .build();

        assertThat(event.getActorType()).isEqualTo(ActorType.API_KEY);
    }

    @Test
    void defaultsMetadataToEmptyMap() {
        AuditEvent event = AuditEvent.builder()
                .tenantId(UUID.randomUUID())
                .action("TEST_ACTION")
                .build();

        assertThat(event.getMetadata()).isEmpty();
    }

    @Test
    void keepsProvidedMetadata() {
        AuditEvent event = AuditEvent.builder()
                .action("TEST_ACTION")
                .metadata(Map.of("plan", "FREE"))
                .build();

        assertThat(event.getMetadata()).containsExactlyEntriesOf(Map.of("plan", "FREE"));
    }

    @Test
    void defaultsOccurredAtToNow() {
        Instant before = Instant.now();

        AuditEvent event = AuditEvent.builder().action("TEST_ACTION").build();

        Instant after = Instant.now();
        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getOccurredAt()).isBetween(before, after);
    }

    @Test
    void keepsProvidedOccurredAt() {
        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");

        AuditEvent event = AuditEvent.builder()
                .action("TEST_ACTION")
                .occurredAt(occurredAt)
                .build();

        assertThat(event.getOccurredAt()).isEqualTo(occurredAt);
    }
}
