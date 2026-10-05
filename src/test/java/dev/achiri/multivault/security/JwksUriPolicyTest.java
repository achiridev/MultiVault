package dev.achiri.multivault.security;

import dev.achiri.multivault.common.exception.JwksUriInvalidaException;
import dev.achiri.multivault.infrastructure.security.jwt.config.JwksProperties;
import dev.achiri.multivault.infrastructure.security.jwt.jwks.JwksUriPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwksUriPolicyTest {

    private JwksUriPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new JwksUriPolicy(new JwksProperties());
    }

    @Test
    void acceptsPublicHttpsUri() {
        assertThatCode(() -> policy.validate("https://93.184.216.34/.well-known/jwks.json"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPlainHttp() {
        assertThatThrownBy(() -> policy.validate("http://93.184.216.34/jwks"))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessageContaining("https");
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertThatThrownBy(() -> policy.validate("file:///etc/passwd"))
                .isInstanceOf(JwksUriInvalidaException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://169.254.169.254/latest/meta-data",
            "https://127.0.0.1/jwks",
            "https://10.1.2.3/jwks",
            "https://192.168.1.10/jwks",
            "https://172.16.5.4/jwks",
            "https://100.64.0.1/jwks",
            "https://198.18.0.1/jwks",
            "https://0.0.0.0/jwks",
            "https://[::1]/jwks",
            "https://[fd00::1]/jwks",
            "https://[fe80::1]/jwks",
            "https://[::ffff:127.0.0.1]/jwks",
            "https://localhost/jwks",
            "https://idp.internal/jwks",
            "https://metadata.google.internal/computeMetadata/v1/"
    })
    void rejectsInternalNetworkTargets(String uri) {
        assertThatThrownBy(() -> policy.validate(uri))
                .isInstanceOf(JwksUriInvalidaException.class);
    }

    @Test
    void rejectsEmbeddedCredentials() {
        assertThatThrownBy(() -> policy.validate("https://user:secret@idp.acme.com/jwks"))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessageContaining("credenciales");
    }

    @Test
    void rejectsFragment() {
        assertThatThrownBy(() -> policy.validate("https://idp.acme.com/jwks#frag"))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessageContaining("fragmento");
    }

    @Test
    void rejectsDisallowedPort() {
        assertThatThrownBy(() -> policy.validate("https://idp.acme.com:8443/jwks"))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessageContaining("puerto");
    }

    @Test
    void rejectsRelativeUri() {
        assertThatThrownBy(() -> policy.validate("/jwks"))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessageContaining("absoluta");
    }

    @Test
    void rejectsMissingHost() {
        assertThatThrownBy(() -> policy.validate("https:///jwks"))
                .isInstanceOf(JwksUriInvalidaException.class);
    }

    @Test
    void rejectsBlankUri() {
        assertThatThrownBy(() -> policy.validate("  "))
                .isInstanceOf(JwksUriInvalidaException.class);
    }

    @Test
    void rejectsUriLongerThanColumnLimit() {
        String uri = "https://idp.acme.com/" + "a".repeat(500);

        assertThatThrownBy(() -> policy.validate(uri))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessageContaining("500");
    }

    @Test
    void rejectsUnresolvableHost() {
        assertThatThrownBy(() -> policy.validate("https://host.invalid-tld-xyz/jwks"))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessageContaining("resuelve");
    }

    @Test
    void acceptsPrivateHostsWhenExplicitlyAllowed() {
        JwksProperties properties = new JwksProperties();
        properties.setAllowHttp(true);
        properties.setAllowPrivateHosts(true);
        properties.setAllowedPorts(List.of("*"));
        JwksUriPolicy lenient = new JwksUriPolicy(properties);

        assertThatCode(() -> lenient.validate("http://localhost:8080/jwks")).doesNotThrowAnyException();
    }

    @Test
    void acceptsHostInsideConfiguredAllowlist() {
        JwksProperties properties = new JwksProperties();
        properties.setAllowedHosts(List.of("93.184.216.34"));
        JwksUriPolicy restricted = new JwksUriPolicy(properties);

        assertThatCode(() -> restricted.validate("https://93.184.216.34/jwks")).doesNotThrowAnyException();
        assertThatThrownBy(() -> restricted.validate("https://1.1.1.1/jwks"))
                .isInstanceOf(JwksUriInvalidaException.class)
                .hasMessageContaining("lista permitida");
    }
}