package dev.achiri.multivault.audit.event;

import dev.achiri.multivault.audit.model.ActorType;

import java.net.InetAddress;
import java.util.UUID;

public record AuditContext(
        UUID tenantId,
        UUID actorUserId,
        UUID apiKeyId,
        ActorType actorType,
        InetAddress ipAddress,
        String userAgent
) {
}
