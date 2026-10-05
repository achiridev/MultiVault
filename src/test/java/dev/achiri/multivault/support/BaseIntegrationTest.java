package dev.achiri.multivault.support;

import dev.achiri.multivault.tenant.model.Tenant;
import dev.achiri.multivault.tenant.model.TenantStatus;
import dev.achiri.multivault.tenant.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Duration;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private static final Duration PROVISIONING_TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private TenantRepository tenantRepository;

    static {
        POSTGRES.start();
        REDIS.start();
    }

    /**
     * El aprovisionamiento es asíncrono (ADR-0016): el tenant autentica desde el
     * primer momento pero no tiene schema hasta que el worker lo procesa.
     */
    protected void awaitActiveTenant(UUID tenantId) {
        long deadline = System.currentTimeMillis() + PROVISIONING_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (tenantRepository.findById(tenantId).orElseThrow().getStatus()
                    != TenantStatus.PENDING_PROVISIONING) {
                return;
            }
            sleep();
        }
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        if (tenant.getStatus() == TenantStatus.PENDING_PROVISIONING) {
            throw new IllegalStateException("El tenant " + tenantId + " no salió de PENDING_PROVISIONING");
        }
    }

    private void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}