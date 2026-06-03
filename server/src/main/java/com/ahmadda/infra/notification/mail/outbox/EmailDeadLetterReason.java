package com.ahmadda.infra.notification.mail.outbox;

public enum EmailDeadLetterReason {
    RETRY_EXHAUSTED,
    PERMANENT_FAILURE
}
