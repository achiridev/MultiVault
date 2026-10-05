package dev.achiri.multivault.infrastructure.security.jwt.jwks;

import dev.achiri.multivault.common.exception.JwksUriInvalidaException;
import dev.achiri.multivault.infrastructure.security.jwt.config.JwksProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwksProvider {

    private static final String REJECTED_MESSAGE = "jwks_uri rechazada: no alcanzable o sin un JWKS válido";

    private final ObjectMapper objectMapper;
    private final JwksUriPolicy jwksUriPolicy;
    private final JwksProperties jwksProperties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Cacheable(cacheNames = "jwks", key = "#jwksUri")
    public List<JwkEntry> fetch(String jwksUri) {
        jwksUriPolicy.validate(jwksUri);
        return download(jwksUri);
    }

    public void probe(String jwksUri) {
        jwksUriPolicy.validate(jwksUri);
        if (!jwksProperties.isVerifyReachability()) {
            return;
        }

        List<JwkEntry> entries;
        try {
            entries = download(jwksUri);
        } catch (RuntimeException e) {
            throw new JwksUriInvalidaException(REJECTED_MESSAGE, e);
        }
        boolean hasUsableRsaKey = entries.stream()
                .anyMatch(entry -> "RSA".equals(entry.kty())
                        && !entry.modulus().isBlank()
                        && !entry.exponent().isBlank());
        if (!hasUsableRsaKey) {
            throw new JwksUriInvalidaException(REJECTED_MESSAGE);
        }
    }

    @CacheEvict(cacheNames = "jwks", key = "#jwksUri")
    public void evict(String jwksUri) {
    }

    private List<JwkEntry> download(String jwksUri) {
        try {
            HttpResponse<InputStream> httpResponse = httpClient.send(
                    request(jwksUri),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (httpResponse.statusCode() != 200) {
                throw new IllegalStateException("JWKS fetch falló con HTTP " + httpResponse.statusCode() + " para " + jwksUri);
            }
            try (InputStream body = httpResponse.body()) {
                return parseKeys(readBounded(body, jwksUri));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("JWKS fetch interrumpido para " + jwksUri, e);
        } catch (IOException e) {
            throw new IllegalStateException("JWKS fetch falló para " + jwksUri, e);
        }
    }

    private HttpRequest request(String jwksUri) {
        return HttpRequest.newBuilder(URI.create(jwksUri))
                .timeout(Duration.ofMillis(jwksProperties.getProbeTimeoutMillis()))
                .GET()
                .build();
    }

    private String readBounded(InputStream body, String jwksUri) throws IOException {
        long maxBodyBytes = jwksProperties.getMaxBodyBytes();
        byte[] bytes = body.readNBytes((int) maxBodyBytes + 1);
        if (bytes.length > maxBodyBytes) {
            throw new IllegalStateException("JWKS excede " + maxBodyBytes + " bytes para " + jwksUri);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private List<JwkEntry> parseKeys(String body) {
        JsonNode keys;
        try {
            keys = objectMapper.readTree(body).get("keys");
        } catch (Exception e) {
            throw new IllegalStateException("JWKS inválido", e);
        }
        if (keys == null || !keys.isArray()) {
            throw new IllegalStateException("JWKS inválido");
        }
        List<JwkEntry> entries = new ArrayList<>();
        for (JsonNode node : keys) {
            entries.add(new JwkEntry(
                    node.path("kid").asText(),
                    node.path("kty").asText(),
                    node.path("alg").asText(),
                    node.path("n").asText(),
                    node.path("e").asText()));
        }
        return entries;
    }
}