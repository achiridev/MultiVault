package dev.achiri.multivault.document.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTest {

    @Test
    void defaultsStatusToActive() {
        assertThat(new Document().getStatus()).isEqualTo(DocumentStatus.ACTIVE);
    }
}
