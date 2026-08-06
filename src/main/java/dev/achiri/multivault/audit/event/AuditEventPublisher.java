package dev.achiri.multivault.audit.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publish(AuditEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
