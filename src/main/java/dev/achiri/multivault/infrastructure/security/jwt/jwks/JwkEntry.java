package dev.achiri.multivault.infrastructure.security.jwt.jwks;

public record JwkEntry(
        String kid,
        String kty,
        String algorithm,
        String modulus,
        String exponent
) {
}
