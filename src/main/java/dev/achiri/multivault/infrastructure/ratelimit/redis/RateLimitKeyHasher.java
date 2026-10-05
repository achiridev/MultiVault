package dev.achiri.multivault.infrastructure.ratelimit.redis;

import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class RateLimitKeyHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int KEY_LENGTH_IN_CHARS = 32;

    private final String prefix;
    private final SecretKeySpec salt;

    public RateLimitKeyHasher(String prefix, String salt) {
        this.prefix = prefix;
        this.salt = new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String hash(String ruleId, RateLimitScope scope, String rawValue) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(salt);
            String material = ruleId + ':' + scope.name() + ':' + rawValue;
            byte[] digest = mac.doFinal(material.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(digest);
            return prefix + ruleId + ':' + scope.name().toLowerCase() + ':' + hex.substring(0, KEY_LENGTH_IN_CHARS);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("no se pudo derivar la clave de rate limit", e);
        }
    }
}