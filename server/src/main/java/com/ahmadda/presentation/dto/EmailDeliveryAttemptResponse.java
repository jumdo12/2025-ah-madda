package com.ahmadda.presentation.dto;

import com.ahmadda.infra.notification.mail.outbox.EmailDeliveryAttempt;
import com.ahmadda.infra.notification.mail.outbox.EmailDeliveryAttemptResult;

import java.time.LocalDateTime;

public record EmailDeliveryAttemptResponse(
        Long attemptId,
        Long outboxId,
        Long recipientId,
        String recipientEmail,
        int attemptNumber,
        EmailDeliveryAttemptResult result,
        String errorMessage,
        LocalDateTime attemptedAt
) {

    public static EmailDeliveryAttemptResponse from(final EmailDeliveryAttempt attempt) {
        return new EmailDeliveryAttemptResponse(
                attempt.getId(),
                attempt.getEmailOutbox()
                        .getId(),
                attempt.getEmailOutboxRecipient()
                        .getId(),
                attempt.getRecipientEmail(),
                attempt.getAttemptNumber(),
                attempt.getResult(),
                attempt.getErrorMessage(),
                attempt.getAttemptedAt()
        );
    }
}
