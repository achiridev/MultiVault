package dev.achiri.multivault.infrastructure.security.jwt;

import io.jsonwebtoken.io.DeserializationException;
import io.jsonwebtoken.io.Deserializer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.Reader;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtJackson3Deserializer implements Deserializer<Map<String, ?>> {

    private static final TypeReference<Map<String, Object>> CLAIMS = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    @Override
    public Map<String, ?> deserialize(byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, CLAIMS);
        } catch (Exception e) {
            throw new DeserializationException("Cannot deserialize JWT payload", e);
        }
    }

    @Override
    public Map<String, ?> deserialize(Reader reader) {
        try {
            return objectMapper.readValue(reader, CLAIMS);
        } catch (Exception e) {
            throw new DeserializationException("Cannot deserialize JWT payload", e);
        }
    }
}
