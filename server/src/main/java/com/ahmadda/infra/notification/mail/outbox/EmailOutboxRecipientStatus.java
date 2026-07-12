package com.ahmadda.infra.notification.mail.outbox;

public enum EmailOutboxRecipientStatus {
    READY,
    PROCESSING,
    RETRY_WAITING,
    RATE_LIMIT_WAITING,
    SENT,
    FAILED,
    CANCELLED
}
