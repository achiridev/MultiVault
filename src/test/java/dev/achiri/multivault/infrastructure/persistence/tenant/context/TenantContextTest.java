package dev.achiri.multivault.infrastructure.persistence.tenant.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void defaultsToNoSchema() {
        assertThat(TenantContext.getSchema()).isNull();
    }

    @Test
    void setsAndClearsSchema() {
        TenantContext.setSchema("mv_acme");
        assertThat(TenantContext.getSchema()).isEqualTo("mv_acme");

        TenantContext.clear();
        assertThat(TenantContext.getSchema()).isNull();
    }

    @Test
    void isolatesBetweenThreads() throws InterruptedException {
        TenantContext.setSchema("mv_alpha");

        AtomicReference<String> otherThreadSchema = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            TenantContext.setSchema("mv_beta");
            otherThreadSchema.set(TenantContext.getSchema());
        });
        thread.start();
        thread.join();

        assertThat(otherThreadSchema.get()).isEqualTo("mv_beta");
        assertThat(TenantContext.getSchema()).isEqualTo("mv_alpha");
    }
}
