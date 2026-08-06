package dev.achiri.multivault.audit.event;

import dev.achiri.multivault.audit.model.AuditLog;
import dev.achiri.multivault.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@Component
@RequiredArgsConstructor
public class AuditLogEventListener {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(AuditEvent event) {
        AuditLog auditLog = new AuditLog();
        auditLog.setTenantId(event.getTenantId());
        auditLog.setActorUserId(event.getActorUserId());
        auditLog.setApiKeyId(event.getApiKeyId());
        auditLog.setActorType(event.getActorType());
        auditLog.setAction(event.getAction());
        auditLog.setResourceType(event.getResourceType());
        auditLog.setResourceId(event.getResourceId());
        auditLog.setIpAddress(event.getIpAddress());
        auditLog.setUserAgent(event.getUserAgent());
        auditLog.setMetadata(toJsonNode(event.getMetadata()));
        auditLogRepository.save(auditLog);
    }

    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return JsonNodeFactory.instance.objectNode();
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        try {
            return objectMapper.valueToTree(value);
        } catch (IllegalArgumentException e) {
            return JsonNodeFactory.instance.objectNode();
        }
    }
}
