package com.ahmadda.infra.notification.mail.outbox;

import com.ahmadda.infra.notification.mail.EmailSender;
import com.ahmadda.infra.notification.mail.exception.EmailOutboxException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.MailParseException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class EmailOutboxDispatcher {

    private static final int MAX_DELIVERY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MINUTES = 5;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final EmailSender emailSender;
    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxRecipientRepository emailOutboxRecipientRepository;
    private final EmailDeliveryAttemptRepository emailDeliveryAttemptRepository;
    private final EmailDeadLetterRepository emailDeadLetterRepository;

    public EmailOutboxDispatcher(
            @Qualifier("failoverEmailSender") final EmailSender emailSender,
            final EmailOutboxRepository emailOutboxRepository,
            final EmailOutboxRecipientRepository emailOutboxRecipientRepository,
            final EmailDeliveryAttemptRepository emailDeliveryAttemptRepository,
            final EmailDeadLetterRepository emailDeadLetterRepository
    ) {
        this.emailSender = emailSender;
        this.emailOutboxRepository = emailOutboxRepository;
        this.emailOutboxRecipientRepository = emailOutboxRecipientRepository;
        this.emailDeliveryAttemptRepository = emailDeliveryAttemptRepository;
        this.emailDeadLetterRepository = emailDeadLetterRepository;
    }

    @Transactional
    public void dispatch(final Long emailOutboxId) {
        dispatchInternal(emailOutboxId);
    }

    private void dispatchInternal(final Long emailOutboxId) {
        EmailOutbox outbox = emailOutboxRepository.findById(emailOutboxId)
                .orElseThrow(() -> new EmailOutboxException("존재하지 않는 아웃박스입니다."));
        LocalDateTime now = LocalDateTime.now();
        List<EmailOutboxRecipient> recipients =
                emailOutboxRecipientRepository.findDispatchableRecipients(emailOutboxId, now);

        if (recipients.isEmpty()) {
            updateOutboxStatus(outbox);
            return;
        }

        recipients.forEach(recipient -> dispatchToRecipient(outbox, recipient));
        updateOutboxStatus(outbox);
    }

    private void dispatchToRecipient(
            final EmailOutbox outbox,
            final EmailOutboxRecipient recipient
    ) {
        recipient.markProcessing();
        int attemptNumber = recipient.nextAttemptNumber();
        LocalDateTime attemptedAt = LocalDateTime.now();

        try {
            emailSender.sendEmails(List.of(recipient.getRecipientEmail()), outbox.getSubject(), outbox.getBody());
            recipient.markSent(attemptedAt, attemptNumber);
            saveAttempt(outbox, recipient, attemptNumber, EmailDeliveryAttemptResult.SUCCESS, null, attemptedAt);
        } catch (RuntimeException e) {
            handleFailure(outbox, recipient, attemptNumber, attemptedAt, e);
        }
    }

    private void handleFailure(
            final EmailOutbox outbox,
            final EmailOutboxRecipient recipient,
            final int attemptNumber,
            final LocalDateTime attemptedAt,
            final RuntimeException exception
    ) {
        String errorMessage = truncate(exception.getMessage());

        if (isPermanentFailure(exception) || attemptNumber >= MAX_DELIVERY_ATTEMPTS) {
            EmailDeadLetterReason reason = isPermanentFailure(exception)
                    ? EmailDeadLetterReason.PERMANENT_FAILURE
                    : EmailDeadLetterReason.RETRY_EXHAUSTED;
            recipient.markFailed(attemptedAt, errorMessage, attemptNumber);
            saveAttempt(
                    outbox,
                    recipient,
                    attemptNumber,
                    EmailDeliveryAttemptResult.DEAD_LETTERED,
                    errorMessage,
                    attemptedAt
            );
            emailDeadLetterRepository.save(EmailDeadLetter.create(outbox, recipient, reason, errorMessage, attemptedAt));
            log.warn(
                    "emailRecipientDeadLettered - emailOutboxId: {}, recipientId: {}, recipientEmail: {}, reason: {}",
                    outbox.getId(),
                    recipient.getId(),
                    recipient.getRecipientEmail(),
                    reason,
                    exception
            );
            return;
        }

        LocalDateTime nextAttemptAt = attemptedAt.plusMinutes(RETRY_DELAY_MINUTES);
        recipient.scheduleRetry(nextAttemptAt, errorMessage, attemptNumber);
        saveAttempt(
                outbox,
                recipient,
                attemptNumber,
                EmailDeliveryAttemptResult.RETRY_SCHEDULED,
                errorMessage,
                attemptedAt
        );
        log.warn(
                "emailRecipientRetryScheduled - emailOutboxId: {}, recipientId: {}, recipientEmail: {}, attempt: {}, nextAttemptAt: {}",
                outbox.getId(),
                recipient.getId(),
                recipient.getRecipientEmail(),
                attemptNumber,
                nextAttemptAt,
                exception
        );
    }

    private void updateOutboxStatus(final EmailOutbox outbox) {
        List<EmailOutboxRecipient> recipients = emailOutboxRecipientRepository.findAllByEmailOutboxId(outbox.getId());

        if (recipients.isEmpty()) {
            outbox.markSent();
            return;
        }

        if (recipients.stream()
                .anyMatch(EmailOutboxRecipient::isPending)) {
            outbox.releaseForRetry();
            return;
        }

        boolean hasSent = recipients.stream()
                .anyMatch(EmailOutboxRecipient::isSent);
        boolean hasFailed = recipients.stream()
                .anyMatch(EmailOutboxRecipient::isFailed);

        if (hasSent && hasFailed) {
            outbox.markPartiallyFailed();
            return;
        }

        if (hasFailed) {
            outbox.markFailed();
            return;
        }

        outbox.markSent();
    }

    private void saveAttempt(
            final EmailOutbox outbox,
            final EmailOutboxRecipient recipient,
            final int attemptNumber,
            final EmailDeliveryAttemptResult result,
            final String errorMessage,
            final LocalDateTime attemptedAt
    ) {
        emailDeliveryAttemptRepository.save(EmailDeliveryAttempt.create(
                outbox,
                recipient,
                attemptNumber,
                result,
                errorMessage,
                attemptedAt
        ));
    }

    private boolean isPermanentFailure(final RuntimeException exception) {
        return exception instanceof MailParseException;
    }

    private String truncate(final String message) {
        if (message == null || message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }

        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
