package com.ahmadda.application.dto;

import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetter;
import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetterReason;

import java.time.LocalDateTime;

public record EmailDeadLetterAlarmPayload(
        Long deadLetterId,
        Long emailOutboxId,
        Long emailOutboxRecipientId,
        String recipientEmail,
        String subject,
        EmailDeadLetterReason reason,
        String errorMessage,
        int attemptCount,
        LocalDateTime failedAt
) {

    public static EmailDeadLetterAlarmPayload from(final EmailDeadLetter deadLetter) {
        return new EmailDeadLetterAlarmPayload(
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
                deadLetter.getFailedAt()
        );
    }
}
