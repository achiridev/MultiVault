package dev.achiri.multivault.audit.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogTest {

    @Test
    void defaultsActorTypeToTenantUser() {
        assertThat(new AuditLog().getActorType()).isEqualTo(ActorType.TENANT_USER);
    }

    @Test
    void defaultsMetadataToEmptyObject() {
        assertThat(new AuditLog().getMetadata()).isNotNull();
        assertThat(new AuditLog().getMetadata().isObject()).isTrue();
        assertThat(new AuditLog().getMetadata().size()).isZero();
    }
}
