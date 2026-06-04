package com.ahmadda.infra.notification.mail.outbox;

public interface EmailOutboxEventPublisher {

    void publishCreated(final Long emailOutboxId);
}
