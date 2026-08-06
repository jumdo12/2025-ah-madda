package com.ahmadda.infra.notification.mail.outbox;

import com.ahmadda.infra.notification.mail.EmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

@RequiredArgsConstructor
public class EmailOutboxDispatcher {

    private final EmailSender emailSender;
    private final EmailOutboxStatusHandler emailOutboxStatusHandler;

    @Async
    public void dispatch(final List<String> recipientEmails, final String subject, final String body) {
        if (recipientEmails.isEmpty()) {
            return;
        }

        try {
            emailSender.sendEmails(recipientEmails, subject, body);
            handleSuccess(recipientEmails, subject, body);
        } catch (RuntimeException ex) {
            handleFailure(recipientEmails, subject, body, ex);
            throw ex;
        }
    }

    private void handleSuccess(final List<String> recipientEmails, final String subject, final String body) {
        for (String recipientEmail : recipientEmails) {
            emailOutboxStatusHandler.handleSuccess(recipientEmail, subject, body);
        }
    }

    private void handleFailure(
            final List<String> recipientEmails,
            final String subject,
            final String body,
            final Throwable cause
    ) {
        for (String recipientEmail : recipientEmails) {
            emailOutboxStatusHandler.handleFailure(recipientEmail, subject, body, cause);
        }
    }
}
