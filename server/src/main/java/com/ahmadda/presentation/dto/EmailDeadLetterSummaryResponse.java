package com.ahmadda.presentation.dto;

import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetter;
import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetterReason;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRecipientStatus;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxStatus;

import java.time.LocalDateTime;

public record EmailDeadLetterSummaryResponse(
        Long deadLetterId,
        Long outboxId,
        Long recipientId,
        String recipientEmail,
        String subject,
        EmailDeadLetterReason reason,
        String errorMessage,
        int attemptCount,
        EmailOutboxStatus outboxStatus,
        EmailOutboxRecipientStatus recipientStatus,
        LocalDateTime failedAt
) {

    public static EmailDeadLetterSummaryResponse from(final EmailDeadLetter deadLetter) {
        return new EmailDeadLetterSummaryResponse(
                deadLetter.getId(),
                deadLetter.getEmailOutbox()
                        .getId(),
                deadLetter.getEmailOutboxRecipient()
                        .getId(),
                deadLetter.getRecipientEmail(),
                deadLetter.getEmailOutbox()
                        .getSubject(),
                deadLetter.getReason(),
                deadLetter.getErrorMessage(),
                deadLetter.getAttemptCount(),
                deadLetter.getEmailOutbox()
                        .getStatus(),
                deadLetter.getEmailOutboxRecipient()
                        .getStatus(),
                deadLetter.getFailedAt()
        );
    }
}
