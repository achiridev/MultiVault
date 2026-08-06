package dev.achiri.multivault.audit.event;

import dev.achiri.multivault.audit.model.ActorType;
import dev.achiri.multivault.audit.model.AuditLog;
import dev.achiri.multivault.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class AuditLogEventListenerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private AuditLogEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuditLogEventListener(auditLogRepository, objectMapper);
    }

    @Test
    void mapsAllEventFieldsOntoAuditLogAndPersists() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID apiKeyId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        AuditEvent event = AuditEvent.builder()
                .tenantId(tenantId)
                .actorUserId(actorUserId)
                .apiKeyId(apiKeyId)
                .actorType(ActorType.PLATFORM_STAFF)
                .action("TENANT_SUSPENDED")
                .resourceType("tenant")
                .resourceId(resourceId)
                .ipAddress(InetAddress.getByName("10.0.0.7"))
                .userAgent("test-agent")
                .metadata(Map.of("reason", "fraud"))
                .build();

        listener.on(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        verifyNoMoreInteractions(auditLogRepository);

        AuditLog saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getActorUserId()).isEqualTo(actorUserId);
        assertThat(saved.getApiKeyId()).isEqualTo(apiKeyId);
        assertThat(saved.getActorType()).isEqualTo(ActorType.PLATFORM_STAFF);
        assertThat(saved.getAction()).isEqualTo("TENANT_SUSPENDED");
        assertThat(saved.getResourceType()).isEqualTo("tenant");
        assertThat(saved.getResourceId()).isEqualTo(resourceId);
        assertThat(saved.getIpAddress()).isEqualTo(InetAddress.getByName("10.0.0.7"));
        assertThat(saved.getUserAgent()).isEqualTo("test-agent");
        assertThat(saved.getMetadata().get("reason").asText()).isEqualTo("fraud");
    }

    @Test
    void persistsEmptyMetadataObjectWhenEventCarriesNone() {
        listener.on(AuditEvent.builder()
                .tenantId(UUID.randomUUID())
                .action("TEST_ACTION")
                .build());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getMetadata()).isNotNull();
        assertThat(captor.getValue().getMetadata().isObject()).isTrue();
        assertThat(captor.getValue().getMetadata().size()).isZero();
    }

    @Test
    void persistsEmptyMetadataObjectWhenExplicitlyNull() {
        listener.on(AuditEvent.builder()
                .tenantId(UUID.randomUUID())
                .action("TEST_ACTION")
                .metadata(null)
                .build());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getMetadata().isObject()).isTrue();
        assertThat(captor.getValue().getMetadata().size()).isZero();
    }

    @Test
    void usesDefaultActorTypeWhenEventCarriesNone() {
        listener.on(AuditEvent.builder()
                .tenantId(UUID.randomUUID())
                .action("TEST_ACTION")
                .build());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getActorType()).isEqualTo(ActorType.SYSTEM);
    }
}
