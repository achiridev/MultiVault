package dev.achiri.multivault.document.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentStatusTest {

    @Test
    void valuesMatchDatabaseCheckConstraint() {
        assertThat(DocumentStatus.values()).containsExactlyInAnyOrder(
                DocumentStatus.ACTIVE,
                DocumentStatus.ARCHIVED);
    }
}
