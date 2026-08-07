package dev.achiri.multivault.audit.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private AuditEventPublisher auditEventPublisher;

    @Test
    void delegatesEventToApplicationEventPublisher() {
        AuditEvent event = AuditEvent.builder().action("TEST_ACTION").build();

        auditEventPublisher.publish(event);

        verify(applicationEventPublisher).publishEvent(event);
    }
}
