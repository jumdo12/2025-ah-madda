package com.ahmadda.infra.notification.mail;

import com.ahmadda.infra.notification.mail.outbox.EmailOutbox;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@RequiredArgsConstructor
public class OutboxEmailSender implements EmailSender {

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailSender delegate;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void sendEmails(final List<String> recipientEmails, final String subject, final String body) {
        saveAndSend(null, recipientEmails, subject, body);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void sendEventEmails(
            final Long eventId,
            final List<String> recipientEmails,
            final String subject,
            final String body
    ) {
        saveAndSend(eventId, recipientEmails, subject, body);
    }

    private void saveAndSend(
            final Long eventId,
            final List<String> recipientEmails,
            final String subject,
            final String body
    ) {
        List<EmailOutbox> outboxes = recipientEmails.stream()
                .map(recipientEmail -> EmailOutbox.createNow(eventId, recipientEmail, subject, body))
                .toList();
        emailOutboxRepository.saveAll(outboxes);

        registerAfterCommitSend(recipientEmails, subject, body);
    }

    private void registerAfterCommitSend(final List<String> recipientEmails, final String subject, final String body) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delegate.sendEmails(recipientEmails, subject, body);
            }
        });
    }
}
