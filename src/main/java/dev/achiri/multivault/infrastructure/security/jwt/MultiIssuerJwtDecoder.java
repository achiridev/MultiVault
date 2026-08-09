package dev.achiri.multivault.infrastructure.security.jwt;

import dev.achiri.multivault.infrastructure.security.codec.JwtJackson3Deserializer;
import dev.achiri.multivault.infrastructure.security.jwt.exception.InvalidJwtException;
import dev.achiri.multivault.infrastructure.security.jwt.jwks.JwkEntry;
import dev.achiri.multivault.infrastructure.security.jwt.jwks.JwksProvider;
import dev.achiri.multivault.infrastructure.security.jwt.model.ValidatedJwt;
import dev.achiri.multivault.tenant.model.TenantIdentityProvider;
import dev.achiri.multivault.tenant.repository.TenantIdentityProviderRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MultiIssuerJwtDecoder {

    private final TenantIdentityProviderRepository identityProviderRepository;
    private final JwksProvider jwksProvider;
    private final ObjectMapper objectMapper;
    private final JwtJackson3Deserializer deserializer;

    public ValidatedJwt authenticate(String token) {
        String issuer = readClaim(token, "iss");
        TenantIdentityProvider provider = identityProviderRepository.findByIssuerAndIsActiveTrue(issuer)
                .orElseThrow(() -> new InvalidJwtException("issuer no configurado: " + issuer));

        String headerAlgorithm = readHeaderAlgorithm(token);
        if (!provider.getAllowedAlgorithms().contains(headerAlgorithm)) {
            throw new InvalidJwtException("algoritmo no permitido: " + headerAlgorithm);
        }

        String keyId = readKeyId(token);
        Claims claims;
        try {
            claims = verifyWithRetry(token, provider, headerAlgorithm, keyId);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidJwtException("JWT inválido", e);
        }

        if (claims.getAudience() == null || !claims.getAudience().contains(provider.getAudience())) {
            throw new InvalidJwtException("audience no válida");
        }

        return new ValidatedJwt(
                provider.getTenantId(),
                claims.getSubject(),
                claims.get("email", String.class),
                claims.get("name", String.class));
    }

    private Claims verifyWithRetry(String token, TenantIdentityProvider provider, String headerAlgorithm, String keyId) {
        try {
            return verify(token, provider, headerAlgorithm, keyId);
        } catch (SignatureException e) {
            jwksProvider.evict(provider.getJwksUri());
            try {
                return verify(token, provider, headerAlgorithm, keyId);
            } catch (JwtException | IllegalArgumentException retry) {
                throw new InvalidJwtException("JWT inválido", retry);
            }
        }
    }

    private Claims verify(String token, TenantIdentityProvider provider, String headerAlgorithm, String keyId) {
        PublicKey publicKey = resolvePublicKey(jwksProvider.fetch(provider.getJwksUri()), keyId);
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(publicKey)
                .deserializeJsonWith(deserializer)
                .clockSkewSeconds(provider.getClockSkewSeconds())
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    private PublicKey resolvePublicKey(List<JwkEntry> entries, String keyId) {
        if (entries.isEmpty()) {
            throw new InvalidJwtException("JWKS sin claves");
        }
        JwkEntry entry = entries.stream()
                .filter(e -> e.kid().equals(keyId))
                .findFirst()
                .orElse(entries.get(0));
        if (!"RSA".equals(entry.kty())) {
            throw new InvalidJwtException("kty no soportado: " + entry.kty());
        }
        try {
            byte[] modulus = Base64.getUrlDecoder().decode(entry.modulus());
            byte[] exponent = Base64.getUrlDecoder().decode(entry.exponent());
            RSAPublicKeySpec spec = new RSAPublicKeySpec(new BigInteger(1, modulus), new BigInteger(1, exponent));
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new InvalidJwtException("clave JWKS inválida", e);
        }
    }

    private String readHeaderAlgorithm(String token) {
        return readJson(token, 0).path("alg").asText();
    }

    private String readKeyId(String token) {
        return readJson(token, 0).path("kid").asText();
    }

    private String readClaim(String token, String claim) {
        return readJson(token, 1).path(claim).asText();
    }

    private JsonNode readJson(String token, int segmentIndex) {
        try {
            String[] segments = token.split("\\.");
            if (segments.length < 2) {
                throw new InvalidJwtException("JWT malformado");
            }
            byte[] decoded = Base64.getUrlDecoder().decode(segments[segmentIndex]);
            return objectMapper.readTree(decoded);
        } catch (IllegalArgumentException | JacksonException | InvalidJwtException e) {
            if (e instanceof InvalidJwtException) {
                throw (InvalidJwtException) e;
            }
            throw new InvalidJwtException("JWT malformado", e);
        }
    }
}
