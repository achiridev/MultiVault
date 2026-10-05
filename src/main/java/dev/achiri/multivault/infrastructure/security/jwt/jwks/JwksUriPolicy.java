package dev.achiri.multivault.infrastructure.security.jwt.jwks;

import dev.achiri.multivault.common.exception.JwksUriInvalidaException;
import dev.achiri.multivault.infrastructure.security.jwt.config.JwksProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class JwksUriPolicy {

    static final int MAX_URI_LENGTH = 500;

    private static final String HTTPS = "https";

    private static final String ANY_PORT = "*";

    private final JwksProperties properties;

    public void validate(String uri) {
        validateSyntax(uri);
        validateHost(parse(uri).getHost());
    }

    public URI parse(String uri) {
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw new JwksUriInvalidaException("jwks_uri no es una URL válida");
        }
    }

    private void validateSyntax(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new JwksUriInvalidaException("jwks_uri es obligatoria");
        }
        if (uri.length() > MAX_URI_LENGTH) {
            throw new JwksUriInvalidaException("jwks_uri excede " + MAX_URI_LENGTH + " caracteres");
        }

        URI parsed = parse(uri);
        if (!parsed.isAbsolute() || parsed.getScheme() == null) {
            throw new JwksUriInvalidaException("jwks_uri debe ser una URL absoluta");
        }
        if (!properties.isAllowHttp() && !HTTPS.equalsIgnoreCase(parsed.getScheme())) {
            throw new JwksUriInvalidaException("jwks_uri debe usar https");
        }
        String scheme = parsed.getScheme().toLowerCase(Locale.ROOT);
        if (!HTTPS.equals(scheme) && !"http".equals(scheme)) {
            throw new JwksUriInvalidaException("esquema no permitido en jwks_uri");
        }
        if (parsed.getHost() == null || parsed.getHost().isBlank()) {
            throw new JwksUriInvalidaException("jwks_uri no tiene host");
        }
        if (parsed.getUserInfo() != null) {
            throw new JwksUriInvalidaException("jwks_uri no admite credenciales embebidas");
        }
        if (parsed.getFragment() != null) {
            throw new JwksUriInvalidaException("jwks_uri no admite fragmento");
        }
        validatePort(parsed.getPort());
        validateHostAllowlist(parsed.getHost());
    }

    private void validatePort(int port) {
        if (port == -1) {
            return;
        }
        List<String> allowed = properties.getAllowedPorts();
        if (allowed != null && (allowed.contains(ANY_PORT) || allowed.contains(String.valueOf(port)))) {
            return;
        }
        throw new JwksUriInvalidaException("puerto no permitido en jwks_uri");
    }

    private void validateHostAllowlist(String host) {
        List<String> allowed = properties.getAllowedHosts();
        if (allowed == null || allowed.isEmpty()) {
            return;
        }
        boolean matched = allowed.stream().anyMatch(entry ->
                host.equalsIgnoreCase(entry) || host.toLowerCase(Locale.ROOT).endsWith("." + entry.toLowerCase(Locale.ROOT)));
        if (!matched) {
            throw new JwksUriInvalidaException("host de jwks_uri fuera de la lista permitida");
        }
    }

    void validateHost(String host) {
        if (properties.isAllowPrivateHosts()) {
            return;
        }
        if (isLocalhostName(host)) {
            throw new JwksUriInvalidaException("jwks_uri no puede apuntar a un host de red interna");
        }

        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new JwksUriInvalidaException("jwks_uri no resuelve a ninguna dirección");
        }
        for (InetAddress address : resolved) {
            if (isForbiddenAddress(address)) {
                throw new JwksUriInvalidaException("jwks_uri no puede apuntar a un host de red interna");
            }
        }
    }

    private boolean isLocalhostName(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || normalized.endsWith(".localhost")
                || normalized.endsWith(".local")
                || normalized.endsWith(".internal");
    }

    private boolean isForbiddenAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            if (isUniqueLocalIpv6(bytes) || isIpv4Mapped(bytes)) {
                return true;
            }
        }
        return isForbiddenIpv4(address.getAddress());
    }

    private boolean isForbiddenIpv4(byte[] bytes) {
        if (bytes.length != 4) {
            return false;
        }
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        if (first == 0 || first >= 224) {
            return true;
        }
        if (first == 100 && second >= 64 && second <= 127) {
            return true;
        }
        return first == 198 && (second == 18 || second == 19);
    }

    private boolean isUniqueLocalIpv6(byte[] bytes) {
        return (Byte.toUnsignedInt(bytes[0]) & 0xFE) == 0xFC;
    }

    private boolean isIpv4Mapped(byte[] bytes) {
        if (bytes.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return Byte.toUnsignedInt(bytes[10]) == 0xFF && Byte.toUnsignedInt(bytes[11]) == 0xFF;
    }
}