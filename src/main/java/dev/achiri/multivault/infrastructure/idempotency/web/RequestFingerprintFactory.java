package dev.achiri.multivault.infrastructure.idempotency.web;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RequestFingerprintFactory {

    private RequestFingerprintFactory() {
    }

    public static String of(String method, String path, byte[] body) {
        StringBuilder material = new StringBuilder()
                .append(method).append('\n')
                .append(path).append('\n');
        if (body != null) {
            material.append(HexFormat.of().formatHex(digest(body)));
        }
        return HexFormat.of().formatHex(digest(material.toString().getBytes(StandardCharsets.UTF_8)));
    }

    public static String of(HttpServletRequest request, byte[] body) {
        return of(request.getMethod(), request.getRequestURI(), body);
    }

    private static byte[] digest(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}