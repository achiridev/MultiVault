package dev.achiri.multivault.infrastructure.security.jwt;

public record JwkEntry(
        String kid,
        String kty,
        String algorithm,
        String modulus,
        String exponent
) {
}
