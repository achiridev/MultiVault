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
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    private static final int HOST_POSTGRES_PORT = Integer.getInteger("mv.testcontainers.postgres-port", 55432);
    private static final int HOST_REDIS_PORT = Integer.getInteger("mv.testcontainers.redis-port", 56379);

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = startPostgres();

    @ServiceConnection
    static final GenericContainer<?> REDIS = startRedis();

    private static final Duration PROVISIONING_TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private TenantRepository tenantRepository;

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

    private static PostgreSQLContainer<?> startPostgres() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine");
        try {
            container.start();
            return container;
        } catch (RuntimeException error) {
            if (!isBridgeNetworkFailure(error)) {
                throw error;
            }
            stopQuietly(container);
            HostPostgresContainer hostContainer = new HostPostgresContainer();
            hostContainer.start();
            return hostContainer;
        }
    }

    private static GenericContainer<?> startRedis() {
        GenericContainer<?> container = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        try {
            container.start();
            return container;
        } catch (RuntimeException error) {
            if (!isBridgeNetworkFailure(error)) {
                throw error;
            }
            stopQuietly(container);
            HostRedisContainer hostContainer = new HostRedisContainer();
            hostContainer.start();
            return hostContainer;
        }
    }

    private static void stopQuietly(GenericContainer<?> container) {
        try {
            container.stop();
        } catch (RuntimeException ignored) {
        }
    }

    private static boolean isBridgeNetworkFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && (message.contains("failed to set up container networking")
                    || message.contains("failed to create endpoint")
                    || message.contains("pair interfaces: operation not supported"))) {
                return true;
            }
        }
        return false;
    }

    static class HostPostgresContainer extends PostgreSQLContainer<HostPostgresContainer> {

        HostPostgresContainer() {
            super("postgres:16-alpine");
            setExposedPorts(List.of());
            withNetworkMode("host");
            withCommand("postgres", "-p", String.valueOf(HOST_POSTGRES_PORT), "-c", "fsync=off");
        }

        @Override
        public Integer getMappedPort(int originalPort) {
            return HOST_POSTGRES_PORT;
        }
    }

    static class HostRedisContainer extends GenericContainer<HostRedisContainer> {

        HostRedisContainer() {
            super("redis:7-alpine");
            setExposedPorts(List.of());
            withNetworkMode("host");
            withCommand("redis-server", "--port", String.valueOf(HOST_REDIS_PORT));
            waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));
        }

        @Override
        public Integer getMappedPort(int originalPort) {
            return HOST_REDIS_PORT;
        }

        @Override
        public Integer getFirstMappedPort() {
            return HOST_REDIS_PORT;
        }
    }
}
