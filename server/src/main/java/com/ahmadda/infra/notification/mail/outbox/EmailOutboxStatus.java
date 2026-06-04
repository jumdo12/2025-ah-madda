package com.ahmadda.infra.notification.mail.outbox;

public enum EmailOutboxStatus {
    READY,
    PROCESSING,
    SENT,
    PARTIAL_CANCELLED,
    PARTIAL_FAILED,
    FAILED,
    CANCELLED
}
