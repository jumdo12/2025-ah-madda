package com.ahmadda.infra.notification.mail.outbox;

import com.ahmadda.infra.notification.mail.EmailSender;
import com.ahmadda.infra.notification.mail.exception.EmailOutboxException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
public class EmailOutboxDispatcher {

    private final EmailSender emailSender;
    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxRecipientRepository emailOutboxRecipientRepository;
    private final EmailOutboxSuccessHandler emailOutboxSuccessHandler;

    public EmailOutboxDispatcher(
            @Qualifier("failoverEmailSender") final EmailSender emailSender,
            final EmailOutboxRepository emailOutboxRepository,
            final EmailOutboxRecipientRepository emailOutboxRecipientRepository,
            final EmailOutboxSuccessHandler emailOutboxSuccessHandler
    ) {
        this.emailSender = emailSender;
        this.emailOutboxRepository = emailOutboxRepository;
        this.emailOutboxRecipientRepository = emailOutboxRecipientRepository;
        this.emailOutboxSuccessHandler = emailOutboxSuccessHandler;
    }

    @Transactional
    public void dispatch(final Long emailOutboxId) {
        dispatchInternal(emailOutboxId);
    }

    private void dispatchInternal(final Long emailOutboxId) {
        EmailOutbox outbox = emailOutboxRepository.findById(emailOutboxId)
                .orElseThrow(() -> new EmailOutboxException("존재하지 않는 아웃박스입니다."));
        List<EmailOutboxRecipient> recipients =
                emailOutboxRecipientRepository.findAllByEmailOutboxId(emailOutboxId);

        if (recipients.isEmpty()) {
            outbox.markSent();
            return;
        }

        List<String> recipientEmails = recipients.stream()
                .map(EmailOutboxRecipient::getRecipientEmail)
                .toList();

        try {
            emailSender.sendEmails(recipientEmails, outbox.getSubject(), outbox.getBody());
        } catch (RuntimeException e) {
            log.warn("emailOutboxDispatchFailed - emailOutboxId: {}", emailOutboxId, e);
            return;
        }

        recipientEmails.forEach(recipientEmail ->
                emailOutboxSuccessHandler.handleSuccess(emailOutboxId, recipientEmail));
    }
}
