package dev.achiri.multivault.audit.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActorTypeTest {

    @Test
    void valuesMatchDatabaseCheckConstraint() {
        assertThat(ActorType.values()).containsExactlyInAnyOrder(
                ActorType.TENANT_USER,
                ActorType.PLATFORM_STAFF,
                ActorType.SYSTEM,
                ActorType.API_KEY);
    }
}
