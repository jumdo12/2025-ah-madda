package com.ahmadda.infra.notification.mail.outbox.worker;

import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetter;
import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetterReason;
import com.ahmadda.infra.notification.mail.outbox.EmailDeliveryAttempt;
import com.ahmadda.infra.notification.mail.outbox.EmailDeliveryAttemptResult;
import com.ahmadda.infra.notification.mail.outbox.EmailOutbox;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRecipient;
import com.ahmadda.infra.notification.mail.outbox.messaging.EmailOutboxEventPublisher;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailDeadLetterRepository;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailDeliveryAttemptRepository;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailOutboxRecipientRepository;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailOutboxRepository;
import com.ahmadda.infra.notification.mail.EmailSender;
import com.ahmadda.infra.notification.mail.exception.EmailOutboxException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.MailParseException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class EmailOutboxDispatcher {

    private static final int MAX_DELIVERY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MINUTES = 5;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    private static final String REFERENCE_NOT_FOUND_MESSAGE = "Referenced event does not exist.";

    private final EmailSender emailSender;
    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxRecipientRepository emailOutboxRecipientRepository;
    private final EmailDeliveryAttemptRepository emailDeliveryAttemptRepository;
    private final EmailDeadLetterRepository emailDeadLetterRepository;
    private final EmailOutboxReferenceValidator emailOutboxReferenceValidator;
    private final EmailOutboxEventPublisher emailOutboxEventPublisher;

    public EmailOutboxDispatcher(
            @Qualifier("failoverEmailSender") final EmailSender emailSender,
            final EmailOutboxRepository emailOutboxRepository,
            final EmailOutboxRecipientRepository emailOutboxRecipientRepository,
            final EmailDeliveryAttemptRepository emailDeliveryAttemptRepository,
            final EmailDeadLetterRepository emailDeadLetterRepository,
            final EmailOutboxReferenceValidator emailOutboxReferenceValidator,
            final EmailOutboxEventPublisher emailOutboxEventPublisher
    ) {
        this.emailSender = emailSender;
        this.emailOutboxRepository = emailOutboxRepository;
        this.emailOutboxRecipientRepository = emailOutboxRecipientRepository;
        this.emailDeliveryAttemptRepository = emailDeliveryAttemptRepository;
        this.emailDeadLetterRepository = emailDeadLetterRepository;
        this.emailOutboxReferenceValidator = emailOutboxReferenceValidator;
        this.emailOutboxEventPublisher = emailOutboxEventPublisher;
    }

    @Transactional
    public void dispatch(final Long emailOutboxId) {
        dispatchInternal(emailOutboxId);
    }

    private void dispatchInternal(final Long emailOutboxId) {
        EmailOutbox outbox = findOutbox(emailOutboxId);
        List<EmailOutboxRecipient> recipients = findDispatchableRecipients(emailOutboxId);

        if (completeIfNoDispatchableRecipients(outbox, recipients)) {
            return;
        }

        if (cancelIfReferenceMissing(outbox)) {
            return;
        }

        boolean hasRetryScheduled = dispatchRecipients(outbox, recipients);
        updateOutboxStatus(outbox);

        if (hasRetryScheduled) {
            publishRetryAfterCommit(outbox.getId());
        }
    }

    private EmailOutbox findOutbox(final Long emailOutboxId) {
        return emailOutboxRepository.findById(emailOutboxId)
                .orElseThrow(() -> new EmailOutboxException("존재하지 않는 아웃박스입니다."));
    }

    private List<EmailOutboxRecipient> findDispatchableRecipients(final Long emailOutboxId) {
        return emailOutboxRecipientRepository.findDispatchableRecipients(emailOutboxId, LocalDateTime.now());
    }

    private boolean completeIfNoDispatchableRecipients(
            final EmailOutbox outbox,
            final List<EmailOutboxRecipient> recipients
    ) {
        if (!recipients.isEmpty()) {
            return false;
        }

        updateOutboxStatus(outbox);
        return true;
    }

    private boolean cancelIfReferenceMissing(final EmailOutbox outbox) {
        if (emailOutboxReferenceValidator.canDispatch(outbox)) {
            return false;
        }

        skipRecipientsForMissingReference(outbox, findPendingRecipients(outbox));
        updateOutboxStatus(outbox);
        return true;
    }

    private List<EmailOutboxRecipient> findPendingRecipients(final EmailOutbox outbox) {
        return emailOutboxRecipientRepository.findAllByEmailOutboxId(outbox.getId())
                .stream()
                .filter(EmailOutboxRecipient::isPending)
                .toList();
    }

    private boolean dispatchRecipients(
            final EmailOutbox outbox,
            final List<EmailOutboxRecipient> recipients
    ) {
        boolean hasRetryScheduled = false;
        for (EmailOutboxRecipient recipient : recipients) {
            hasRetryScheduled = dispatchToRecipient(outbox, recipient) || hasRetryScheduled;
        }

        return hasRetryScheduled;
    }

    private void skipRecipientsForMissingReference(
            final EmailOutbox outbox,
            final List<EmailOutboxRecipient> recipients
    ) {
        LocalDateTime attemptedAt = LocalDateTime.now();

        recipients.forEach(recipient -> {
            int attemptNumber = recipient.nextAttemptNumber();
            recipient.markCancelled(REFERENCE_NOT_FOUND_MESSAGE, attemptNumber);
            saveAttempt(
                    outbox,
                    recipient,
                    attemptNumber,
                    EmailDeliveryAttemptResult.SKIPPED,
                    REFERENCE_NOT_FOUND_MESSAGE,
                    attemptedAt
            );
        });
        log.warn(
                "emailOutboxSkippedForMissingReference - emailOutboxId: {}, referenceType: {}, referenceId: {}",
                outbox.getId(),
                outbox.getReferenceType(),
                outbox.getReferenceId()
        );
    }

    private boolean dispatchToRecipient(
            final EmailOutbox outbox,
            final EmailOutboxRecipient recipient
    ) {
        recipient.markProcessing();
        int attemptNumber = recipient.nextAttemptNumber();
        LocalDateTime attemptedAt = LocalDateTime.now();

        try {
            emailSender.sendEmails(List.of(recipient.getRecipientEmail()), outbox.getSubject(), outbox.getBody());
            markRecipientSent(outbox, recipient, attemptNumber, attemptedAt);
            return false;
        } catch (RuntimeException e) {
            return handleFailure(outbox, recipient, attemptNumber, attemptedAt, e);
        }
    }

    private void markRecipientSent(
            final EmailOutbox outbox,
            final EmailOutboxRecipient recipient,
            final int attemptNumber,
            final LocalDateTime attemptedAt
    ) {
        recipient.markSent(attemptedAt, attemptNumber);
        saveAttempt(outbox, recipient, attemptNumber, EmailDeliveryAttemptResult.SUCCESS, null, attemptedAt);
    }

    private boolean handleFailure(
            final EmailOutbox outbox,
            final EmailOutboxRecipient recipient,
            final int attemptNumber,
            final LocalDateTime attemptedAt,
            final RuntimeException exception
    ) {
        String errorMessage = truncate(exception.getMessage());

        if (shouldDeadLetter(exception, attemptNumber)) {
            return deadLetterRecipient(outbox, recipient, attemptNumber, attemptedAt, errorMessage, exception);
        }

        scheduleRetry(outbox, recipient, attemptNumber, attemptedAt, errorMessage, exception);
        return true;
    }

    private boolean shouldDeadLetter(final RuntimeException exception, final int attemptNumber) {
        return isPermanentFailure(exception) || attemptNumber >= MAX_DELIVERY_ATTEMPTS;
    }

    private boolean deadLetterRecipient(
            final EmailOutbox outbox,
            final EmailOutboxRecipient recipient,
            final int attemptNumber,
            final LocalDateTime attemptedAt,
            final String errorMessage,
            final RuntimeException exception
    ) {
        EmailDeadLetterReason reason = deadLetterReason(exception);
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
        return false;
    }

    private EmailDeadLetterReason deadLetterReason(final RuntimeException exception) {
        if (isPermanentFailure(exception)) {
            return EmailDeadLetterReason.PERMANENT_FAILURE;
        }

        return EmailDeadLetterReason.RETRY_EXHAUSTED;
    }

    private void scheduleRetry(
            final EmailOutbox outbox,
            final EmailOutboxRecipient recipient,
            final int attemptNumber,
            final LocalDateTime attemptedAt,
            final String errorMessage,
            final RuntimeException exception
    ) {
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

    private void publishRetryAfterCommit(final Long emailOutboxId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                try {
                    emailOutboxEventPublisher.publishRetry(emailOutboxId);
                } catch (RuntimeException e) {
                    log.warn("emailOutboxRetryPublishFailed - emailOutboxId: {}", emailOutboxId, e);
                }
            }
        });
    }

    private void updateOutboxStatus(final EmailOutbox outbox) {
        List<EmailOutboxRecipient> recipients = findAllRecipients(outbox);

        if (markSentIfNoRecipients(outbox, recipients)) {
            return;
        }

        if (releaseIfAnyRecipientPending(outbox, recipients)) {
            return;
        }

        markCompletedStatus(outbox, recipients);
    }

    private List<EmailOutboxRecipient> findAllRecipients(final EmailOutbox outbox) {
        return emailOutboxRecipientRepository.findAllByEmailOutboxId(outbox.getId());
    }

    private boolean markSentIfNoRecipients(
            final EmailOutbox outbox,
            final List<EmailOutboxRecipient> recipients
    ) {
        if (!recipients.isEmpty()) {
            return false;
        }

        outbox.markSent();
        return true;
    }

    private boolean releaseIfAnyRecipientPending(
            final EmailOutbox outbox,
            final List<EmailOutboxRecipient> recipients
    ) {
        if (recipients.stream()
                .noneMatch(EmailOutboxRecipient::isPending)) {
            return false;
        }

        outbox.releaseForRetry();
        return true;
    }

    private void markCompletedStatus(
            final EmailOutbox outbox,
            final List<EmailOutboxRecipient> recipients
    ) {
        boolean hasSent = recipients.stream()
                .anyMatch(EmailOutboxRecipient::isSent);
        boolean hasFailed = recipients.stream()
                .anyMatch(EmailOutboxRecipient::isFailed);
        boolean hasCancelled = recipients.stream()
                .anyMatch(EmailOutboxRecipient::isCancelled);

        if (hasSent && hasFailed) {
            outbox.markPartiallyFailed();
            return;
        }

        if (hasFailed) {
            outbox.markFailed();
            return;
        }

        if (hasSent && hasCancelled) {
            outbox.markPartiallyCancelled();
            return;
        }

        if (hasCancelled) {
            outbox.markCancelled();
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
