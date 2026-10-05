package dev.achiri.multivault.infrastructure.idempotency.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RequestFingerprintFactoryTest {

    @Test
    void producesTheSameFingerprintForTheSameMethodPathAndBody() {
        byte[] body = "{\"name\":\"acme\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(RequestFingerprintFactory.of("POST", "/api/v1/tenants", body))
                .isEqualTo(RequestFingerprintFactory.of("POST", "/api/v1/tenants", body.clone()));
    }

    @Test
    void producesDifferentFingerprintsForDifferentBodies() {
        String first = RequestFingerprintFactory.of("POST", "/api/v1/tenants",
                "{\"name\":\"acme\"}".getBytes(StandardCharsets.UTF_8));
        String second = RequestFingerprintFactory.of("POST", "/api/v1/tenants",
                "{\"name\":\"other\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void producesDifferentFingerprintsForDifferentPaths() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        assertThat(RequestFingerprintFactory.of("POST", "/api/v1/tenants", body))
                .isNotEqualTo(RequestFingerprintFactory.of("POST", "/api/v1/other", body));
    }

    @Test
    void producesDifferentFingerprintsForDifferentMethods() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        assertThat(RequestFingerprintFactory.of("POST", "/api/v1/tenants", body))
                .isNotEqualTo(RequestFingerprintFactory.of("PUT", "/api/v1/tenants", body));
    }

    @Test
    void distinguishesNullBodyFromEmptyBody() {
        String withNull = RequestFingerprintFactory.of("POST", "/api/v1/tenants", null);
        String withEmpty = RequestFingerprintFactory.of("POST", "/api/v1/tenants", new byte[0]);

        assertThat(withNull).isNotEqualTo(withEmpty);
    }

    @Test
    void producesSixtyFourHexCharacters() {
        String fingerprint = RequestFingerprintFactory.of("POST", "/api/v1/tenants", new byte[0]);

        assertThat(fingerprint).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void cannotBeConfusedByFieldReorderingThatChangesContent() {
        String first = RequestFingerprintFactory.of("POST", "/api/v1/tenants",
                "{\"a\":1,\"b\":2}".getBytes(StandardCharsets.UTF_8));
        String second = RequestFingerprintFactory.of("POST", "/api/v1/tenants",
                "{\"b\":2,\"a\":1}".getBytes(StandardCharsets.UTF_8));

        assertThat(first).isNotEqualTo(second);
    }
}