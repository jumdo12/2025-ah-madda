package com.ahmadda.infra.notification.mail.outbox;

public enum EmailDeliveryAttemptResult {
    SUCCESS,
    RETRY_SCHEDULED,
    DEAD_LETTERED,
    SKIPPED
}
