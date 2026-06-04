package com.ahmadda.infra.notification.mail.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "mail.outbox.rabbitmq",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class NoopEmailOutboxEventPublisher implements EmailOutboxEventPublisher {

    @Override
    public void publishCreated(final Long emailOutboxId) {
    }
}
