package dev.achiri.multivault.infrastructure.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwksProvider {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Cacheable(cacheNames = "jwks", key = "#jwksUri")
    public List<JwkEntry> fetch(String jwksUri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(jwksUri))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (httpResponse.statusCode() != 200) {
                throw new IllegalStateException("JWKS fetch falló con HTTP " + httpResponse.statusCode() + " para " + jwksUri);
            }
            return parseKeys(httpResponse.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("JWKS fetch interrumpido para " + jwksUri, e);
        } catch (IOException e) {
            throw new IllegalStateException("JWKS fetch falló para " + jwksUri, e);
        }
    }

    @CacheEvict(cacheNames = "jwks", key = "#jwksUri")
    public void evict(String jwksUri) {
    }

    private List<JwkEntry> parseKeys(String body) {
        try {
            JsonNode keys = objectMapper.readTree(body).get("keys");
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
        } catch (Exception e) {
            throw new IllegalStateException("JWKS inválido", e);
        }
    }
}
