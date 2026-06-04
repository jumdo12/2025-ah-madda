package com.ahmadda.infra.notification.mail;

import com.ahmadda.infra.notification.mail.outbox.EmailOutbox;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxEventPublisher;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRecipient;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRecipientRepository;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class OutboxEmailSender implements EmailOutboxSender {

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxRecipientRepository emailOutboxRecipientRepository;
    private final EmailOutboxEventPublisher emailOutboxEventPublisher;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void sendEmails(final List<String> recipientEmails, final String subject, final String body) {
        saveOutbox(recipientEmails, EmailOutbox.createReadyNow(subject, body));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void sendEventEmails(
            final List<String> recipientEmails,
            final String subject,
            final String body,
            final Long eventId
    ) {
        saveOutbox(recipientEmails, EmailOutbox.createReadyEventNow(subject, body, eventId));
    }

    private void saveOutbox(final List<String> recipientEmails, final EmailOutbox outbox) {
        List<EmailOutboxRecipient> recipients = recipientEmails.stream()
                .map(email -> EmailOutboxRecipient.create(outbox, email))
                .toList();
        emailOutboxRepository.save(outbox);
        emailOutboxRecipientRepository.saveAll(recipients);
        publishAfterCommit(outbox.getId());
    }

    private void publishAfterCommit(final Long emailOutboxId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                try {
                    emailOutboxEventPublisher.publishCreated(emailOutboxId);
                } catch (RuntimeException e) {
                    log.warn("emailOutboxPublishFailed - emailOutboxId: {}", emailOutboxId, e);
                }
            }
        });
    }
}
