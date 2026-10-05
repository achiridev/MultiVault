package dev.achiri.multivault.infrastructure.idempotency.model;

public record StoredResponse(
        int status,
        byte[] body,
        String contentType
) {

    public boolean isSuccessful() {
        return status >= 200 && status < 300;
    }
}