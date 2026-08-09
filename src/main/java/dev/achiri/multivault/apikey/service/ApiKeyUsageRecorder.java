package dev.achiri.multivault.apikey.service;

import dev.achiri.multivault.apikey.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiKeyUsageRecorder {

    private final ApiKeyRepository apiKeyRepository;

    @Async
    @Transactional
    public void recordUsage(UUID apiKeyId) {
        apiKeyRepository.findById(apiKeyId)
                .ifPresent(apiKey -> apiKey.setLastUsedAt(Instant.now()));
    }
}
