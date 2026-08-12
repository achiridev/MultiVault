package dev.achiri.multivault.document.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentVersionTest {

    @Test
    void defaultsMetadataToEmptyObject() {
        assertThat(new DocumentVersion().getMetadata()).isNotNull();
        assertThat(new DocumentVersion().getMetadata().isObject()).isTrue();
        assertThat(new DocumentVersion().getMetadata().size()).isZero();
    }
}
