package dev.achiri.multivault.infrastructure.persistence.auditing;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component("auditorAwareImpl")
public class AuditorAwareImpl implements AuditorAware<UUID> {
    private static final UUID SYSTEM_AUDITOR = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Override
    public Optional<UUID> getCurrentAuditor() {
        return Optional.of(SYSTEM_AUDITOR);
    }
}
