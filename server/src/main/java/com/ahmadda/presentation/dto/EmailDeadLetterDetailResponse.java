package com.ahmadda.presentation.dto;

import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetter;
import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetterReason;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRecipientStatus;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxStatus;

import java.time.LocalDateTime;

public record EmailDeadLetterDetailResponse(
        Long deadLetterId,
        Long outboxId,
        Long recipientId,
        String recipientEmail,
        String subject,
        String body,
        EmailDeadLetterReason reason,
        String errorMessage,
        int attemptCount,
        EmailOutboxStatus outboxStatus,
        EmailOutboxRecipientStatus recipientStatus,
        String recipientLastErrorMessage,
        LocalDateTime outboxCreatedAt,
        LocalDateTime failedAt
) {

    public static EmailDeadLetterDetailResponse from(final EmailDeadLetter deadLetter) {
        return new EmailDeadLetterDetailResponse(
                deadLetter.getId(),
                deadLetter.getEmailOutbox()
                        .getId(),
                deadLetter.getEmailOutboxRecipient()
                        .getId(),
                deadLetter.getRecipientEmail(),
                deadLetter.getEmailOutbox()
                        .getSubject(),
                deadLetter.getEmailOutbox()
                        .getBody(),
                deadLetter.getReason(),
                deadLetter.getErrorMessage(),
                deadLetter.getAttemptCount(),
                deadLetter.getEmailOutbox()
                        .getStatus(),
                deadLetter.getEmailOutboxRecipient()
                        .getStatus(),
                deadLetter.getEmailOutboxRecipient()
                        .getLastErrorMessage(),
                deadLetter.getEmailOutbox()
                        .getCreatedAt(),
                deadLetter.getFailedAt()
        );
    }
}
