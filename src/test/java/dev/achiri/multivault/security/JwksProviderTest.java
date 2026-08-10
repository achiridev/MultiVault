package dev.achiri.multivault.security;

import com.sun.net.httpserver.HttpServer;
import dev.achiri.multivault.infrastructure.security.jwt.jwks.JwkEntry;
import dev.achiri.multivault.infrastructure.security.jwt.jwks.JwksProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwksProviderTest {

    private HttpServer server;
    private JwksProvider jwksProvider;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        jwksProvider = new JwksProvider(new JsonMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void fetchesAndParsesJwks() {
        String uri = serve("""
                {"keys":[{"kid":"k1","kty":"RSA","alg":"RS256","n":"abc","e":"AQAB"},
                          {"kid":"k2","kty":"EC","alg":"ES256","n":"","e":""}]}
                """, 200);

        List<JwkEntry> entries = jwksProvider.fetch(uri);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).kid()).isEqualTo("k1");
        assertThat(entries.get(0).kty()).isEqualTo("RSA");
        assertThat(entries.get(0).algorithm()).isEqualTo("RS256");
        assertThat(entries.get(0).modulus()).isEqualTo("abc");
        assertThat(entries.get(0).exponent()).isEqualTo("AQAB");
        assertThat(entries.get(1).kty()).isEqualTo("EC");
    }

    @Test
    void throwsOnHttpErrorStatus() {
        String uri = serve("error", 500);

        assertThatThrownBy(() -> jwksProvider.fetch(uri))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void throwsOnInvalidJsonBody() {
        String uri = serve("not-json", 200);

        assertThatThrownBy(() -> jwksProvider.fetch(uri))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWKS inválido");
    }

    @Test
    void throwsOnMissingKeysField() {
        String uri = serve("{\"foo\":\"bar\"}", 200);

        assertThatThrownBy(() -> jwksProvider.fetch(uri))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWKS inválido");
    }

    private String serve(String body, int status) {
        server.createContext("/jwks", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/jwks";
    }
}
