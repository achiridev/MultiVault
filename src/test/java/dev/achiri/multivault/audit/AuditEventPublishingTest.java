package dev.achiri.multivault.audit;

import dev.achiri.multivault.audit.event.AuditEvent;
import dev.achiri.multivault.audit.event.AuditEventPublisher;
import dev.achiri.multivault.audit.model.ActorType;
import dev.achiri.multivault.audit.model.AuditLog;
import dev.achiri.multivault.audit.repository.AuditLogRepository;
import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class AuditEventPublishingTest {

    @Autowired
    private AuditEventPublisher auditEventPublisher;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setName("Audit Test");
        tenant.setSchemaName("mv_audit_test");
        tenantRepository.save(tenant);
    }

    @AfterEach
    void tearDown() {
        auditLogRepository.deleteAll(auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId()));
        tenantRepository.delete(tenant);
    }

    @Test
    void persistsAuditLogAfterSuccessfulTransaction() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> auditEventPublisher.publish(AuditEvent.builder()
                .tenantId(tenant.getId())
                .actorType(ActorType.SYSTEM)
                .action("TENANT_CREATED")
                .resourceType("tenant")
                .resourceId(tenant.getId())
                .ipAddress(InetAddress.getLoopbackAddress())
                .metadata(Map.of("plan", "FREE"))
                .build()));

        List<AuditLog> logs = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getAction()).isEqualTo("TENANT_CREATED");
        assertThat(logs.getFirst().getActorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(logs.getFirst().getMetadata().get("plan").asText()).isEqualTo("FREE");
    }

    @Test
    void doesNotPersistAuditLogWhenTransactionRollsBack() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            tx.executeWithoutResult(status -> {
                auditEventPublisher.publish(AuditEvent.builder()
                        .tenantId(tenant.getId())
                        .actorType(ActorType.SYSTEM)
                        .action("TENANT_CREATED")
                        .resourceType("tenant")
                        .resourceId(tenant.getId())
                        .build());
                throw new IllegalStateException("force rollback");
            });
        } catch (IllegalStateException ignored) {
        }

        assertThat(auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId())).isEmpty();
    }
}
