package com.ahmadda.infra.notification.mail;

import com.ahmadda.infra.notification.mail.outbox.EmailOutbox;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRecipient;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailOutboxRecipientRepository;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
public class OutboxEmailSender implements EmailOutboxSender {

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxRecipientRepository emailOutboxRecipientRepository;

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
    }
}
