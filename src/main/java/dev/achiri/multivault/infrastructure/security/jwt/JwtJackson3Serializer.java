package dev.achiri.multivault.infrastructure.security.jwt;

import io.jsonwebtoken.io.SerializationException;
import io.jsonwebtoken.io.Serializer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtJackson3Serializer implements Serializer<Map<String, ?>> {

    private final ObjectMapper objectMapper;

    @Override
    public byte[] serialize(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new SerializationException("Cannot serialize JWT payload", e);
        }
    }

    @Override
    public void serialize(Map<String, ?> value, OutputStream outputStream) {
        try {
            objectMapper.writeValue(outputStream, value);
        } catch (Exception e) {
            throw new SerializationException("Cannot serialize JWT payload", e);
        }
    }
}
