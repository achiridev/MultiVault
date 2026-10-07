package dev.achiri.multivault.infrastructure.idempotency.web;

import dev.achiri.multivault.common.exception.ClaveIdempotenciaReutilizadaException;
import dev.achiri.multivault.common.exception.SolicitudIdempotenteEnCursoException;
import dev.achiri.multivault.infrastructure.idempotency.config.IdempotencyProperties;
import dev.achiri.multivault.infrastructure.idempotency.handler.IdempotencyErrorWriter;
import dev.achiri.multivault.infrastructure.idempotency.model.IdempotencyRecord;
import dev.achiri.multivault.infrastructure.idempotency.model.StoredResponse;
import dev.achiri.multivault.infrastructure.idempotency.spi.IdempotencyStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "Idempotency-Key";
    static final String REPLAYED_HEADER_NAME = "Idempotency-Replayed";
    static final String RETRY_AFTER_HEADER = "Retry-After";
    static final String RETRY_AFTER_SECONDS = "2";

    private final IdempotencyStore store;
    private final IdempotencyProperties properties;
    private final IdempotencyErrorWriter errorWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Optional<String> header = headerOf(request);
        if (header.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        Optional<String> invalidReason = IdempotencyKeyValidator.invalidReason(header.get());
        if (invalidReason.isPresent()) {
            errorWriter.write(response, HttpStatus.BAD_REQUEST.value(), invalidReason.get());
            return;
        }
        String key = header.get().trim();
        byte[] body = readBody(request, response);
        if (body == null) {
            return;
        }
        String fingerprint = RequestFingerprintFactory.of(request.getMethod(), request.getRequestURI(), body);

        if (store.claim(key, IdempotencyRecord.inProgress(fingerprint, Instant.now()))) {
            processFreshRequest(request, body, response, filterChain, key, fingerprint);
            return;
        }
        handleExistingRecord(response, key, fingerprint);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || HttpMethod.GET.name().equals(request.getMethod());
    }

    private void processFreshRequest(HttpServletRequest request, byte[] body, HttpServletResponse response,
                                     FilterChain filterChain, String key, String fingerprint)
            throws ServletException, IOException {

        HttpServletRequest bufferedRequest = new BufferedBodyRequestWrapper(request, body);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(bufferedRequest, cachedResponse);
            completeOrRelease(cachedResponse, key, fingerprint);
        } finally {
            cachedResponse.copyBodyToResponse();
        }
    }

    private void completeOrRelease(ContentCachingResponseWrapper response, String key, String fingerprint) {
        StoredResponse stored = new StoredResponse(
                response.getStatus(),
                response.getContentAsByteArray(),
                contentTypeOf(response));
        if (stored.isSuccessful()) {
            store.store(key, IdempotencyRecord.completed(fingerprint, stored, Instant.now()));
        } else {
            store.release(key);
        }
    }

    private void handleExistingRecord(HttpServletResponse response, String key, String fingerprint)
            throws IOException {

        IdempotencyRecord existing = store.find(key).orElse(null);
        if (existing == null || !existing.matchesFingerprint(fingerprint)) {
            errorWriter.write(response, HttpStatus.UNPROCESSABLE_ENTITY.value(),
                    new ClaveIdempotenciaReutilizadaException(key).getMessage());
            return;
        }
        if (existing.response() == null) {
            response.setHeader(RETRY_AFTER_HEADER, RETRY_AFTER_SECONDS);
            errorWriter.write(response, HttpStatus.CONFLICT.value(),
                    new SolicitudIdempotenteEnCursoException(key).getMessage());
            return;
        }
        replay(response, existing.response());
    }

    private void replay(HttpServletResponse response, StoredResponse stored) throws IOException {
        response.setStatus(stored.status());
        response.setContentType(stored.contentType());
        response.setHeader(REPLAYED_HEADER_NAME, "true");
        response.getOutputStream().write(stored.body());
        response.flushBuffer();
    }

    private byte[] readBody(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request.getContentLength() > properties.maxBodyBytes()) {
            errorWriter.write(response, HttpStatus.PAYLOAD_TOO_LARGE.value(),
                    "El cuerpo excede el máximo de " + properties.maxBodyBytes() + " bytes para idempotencia");
            return null;
        }
        byte[] body = request.getInputStream().readNBytes(properties.maxBodyBytes() + 1);
        if (body.length > properties.maxBodyBytes()) {
            errorWriter.write(response, HttpStatus.PAYLOAD_TOO_LARGE.value(),
                    "El cuerpo excede el máximo de " + properties.maxBodyBytes() + " bytes para idempotencia");
            return null;
        }
        return body;
    }

    private Optional<String> headerOf(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(HEADER_NAME)).map(String::trim).filter(key -> !key.isEmpty());
    }

    private static String contentTypeOf(ContentCachingResponseWrapper response) {
        String contentType = response.getContentType();
        return contentType == null ? MediaType.APPLICATION_JSON_VALUE : contentType;
    }
}