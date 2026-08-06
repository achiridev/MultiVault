package dev.achiri.multivault.audit.event;

import dev.achiri.multivault.audit.model.ActorType;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@RequiredArgsConstructor
public class AuditEvent {

    private final UUID tenantId;
    private final UUID actorUserId;
    private final UUID apiKeyId;

    @Builder.Default
    private final ActorType actorType = ActorType.SYSTEM;

    private final String action;
    private final String resourceType;
    private final UUID resourceId;
    private final InetAddress ipAddress;
    private final String userAgent;

    @Builder.Default
    private final Map<String, Object> metadata = Map.of();

    @Builder.Default
    private final Instant occurredAt = Instant.now();
}
