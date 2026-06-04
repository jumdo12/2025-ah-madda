package com.ahmadda.infra.notification.mail.outbox.messaging;

public interface EmailOutboxEventPublisher {

    void publishCreated(final Long emailOutboxId);

    void publishRetry(final Long emailOutboxId);

    void publishDeadLetter(final String message, final String reason);
}
