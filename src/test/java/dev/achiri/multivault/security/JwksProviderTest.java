package dev.achiri.multivault.security;

import com.sun.net.httpserver.HttpServer;
import dev.achiri.multivault.common.exception.JwksUriInvalidaException;
import dev.achiri.multivault.infrastructure.security.jwt.config.JwksProperties;
import dev.achiri.multivault.infrastructure.security.jwt.jwks.JwkEntry;
import dev.achiri.multivault.infrastructure.security.jwt.jwks.JwksProvider;
import dev.achiri.multivault.infrastructure.security.jwt.jwks.JwksUriPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwksProviderTest {

    private static final String REJECTED_MESSAGE = "jwks_uri rechazada: no alcanzable o sin un JWKS válido";

    private static final String RSA_JWKS = """
            {"keys":[{"kid":"k1","kty":"RSA","alg":"RS256","n":"abc","e":"AQAB"}]}
            """;

    private HttpServer server;
    private int port;
    private JwksProvider jwksProvider;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        jwksProvider = provider(lenientProperties());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void fetchesAndParsesJwks() {
        String uri = serve(RSA_JWKS, 200, null);

        List<JwkEntry> entries = jwksProvider.fetch(uri);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).kid()).isEqualTo("k1");
        assertThat(entries.get(0).kty()).isEqualTo("RSA");
        assertThat(entries.get(0).algorithm()).isEqualTo("RS256");
        assertThat(entries.get(0).modulus()).isEqualTo("abc");
        assertThat(entries.get(0).exponent()).isEqualTo("AQAB");
    }

    @Test
    void parsesMultipleKeysIncludingNonRsa() {
        String uri = serve("""
                {"keys":[{"kid":"k1","kty":"RSA","alg":"RS256","n":"abc","e":"AQAB"},
                          {"kid":"k2","kty":"EC","alg":"ES256","n":"","e":""}]}
                """, 200, null);

        List<JwkEntry> entries = jwksProvider.fetch(uri);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(1).kty()).isEqualTo("EC");
    }

    @Test
    void throwsOnHttpErrorStatus() {
        String uri = serve("error", 500, null);

        assertThatThrownBy(() -> jwksProvider.fetch(uri))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void throwsOnInvalidJsonBody() {
        String uri = serve("not-json", 200, null);

        assertThatThrownBy(() -> jwksProvider.fetch(uri))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWKS inválido");
    }

    @Test
    void throwsOnMissingKeysField() {
        String uri = serve("{\"foo\":\"bar\"}", 200, null);

        assertThatThrownBy(() -> jwksProvider.fetch(uri))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWKS inválido");
    }

    @Test
    void probeAcceptsValidRsaJwks() {
        String uri = serve(RSA_JWKS, 200, null);

        assertThatCode(() -> jwksProvider.probe(uri)).doesNotThrowAnyException();
    }

    @Test
    void probeRejectsJwksWithoutUsableRsaKey() {
        String uri = serve("{\"keys\":[{\"kid\":\"k1\",\"kty\":\"EC\",\"alg\":\"ES256\",\"n\":\"\",\"e\":\"\"}]}", 200, null);

        assertThatThrownBy(() -> jwksProvider.probe(uri))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessage(REJECTED_MESSAGE);
    }

    @Test
    void probeRejectsUnreachableJwksWithSameMessageAsInvalidBody() {
        assertThatThrownBy(() -> jwksProvider.probe("https://localhost:" + port + "/jwks"))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessage(REJECTED_MESSAGE);
    }

    @Test
    void probeRejectsNonJsonBodyWithSameMessage() {
        String uri = serve("<html>login</html>", 200, null);

        assertThatThrownBy(() -> jwksProvider.probe(uri))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessage(REJECTED_MESSAGE);
    }

    @Test
    void probeIsSkippedWhenReachabilityVerificationDisabled() {
        JwksProperties properties = lenientProperties();
        properties.setVerifyReachability(false);
        JwksProvider provider = provider(properties);

        assertThatCode(() -> provider.probe("https://localhost:" + port + "/jwks")).doesNotThrowAnyException();
    }

    @Test
    void rejectsUriPointingToLoopback() {
        JwksProvider strict = provider(new JwksProperties());

        assertThatThrownBy(() -> strict.fetch("https://127.0.0.1/jwks"))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessageContaining("red interna");
    }

    @Test
    void rejectsPlainHttpWhenSchemeNotAllowed() {
        JwksProvider strict = provider(new JwksProperties());

        assertThatThrownBy(() -> strict.fetch("http://idp.acme.com/jwks"))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessageContaining("https");
    }

    @Test
    void doesNotFollowRedirects() {
        String uri = serve(RSA_JWKS, 302, "http://localhost:" + port + "/elsewhere");

        assertThatThrownBy(() -> jwksProvider.fetch(uri))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 302");
    }

    @Test
    void throwsWhenBodyExceedsConfiguredLimit() {
        JwksProperties properties = lenientProperties();
        properties.setMaxBodyBytes(16);
        JwksProvider bounded = provider(properties);
        String uri = serve(RSA_JWKS, 200, null);

        assertThatThrownBy(() -> bounded.fetch(uri))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("excede");
    }

    private JwksProperties lenientProperties() {
        JwksProperties properties = new JwksProperties();
        properties.setAllowHttp(true);
        properties.setAllowPrivateHosts(true);
        properties.setAllowedPorts(List.of("*"));
        return properties;
    }

    private JwksProvider provider(JwksProperties properties) {
        return new JwksProvider(new JsonMapper(), new JwksUriPolicy(properties), properties);
    }

    private String serve(String body, int status, String location) {
        server.createContext("/jwks", exchange -> {
            if (location != null) {
                exchange.getResponseHeaders().add("Location", location);
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return "http://localhost:" + port + "/jwks";
    }
}