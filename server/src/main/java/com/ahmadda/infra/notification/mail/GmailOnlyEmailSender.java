package com.ahmadda.infra.notification.mail;

import com.ahmadda.infra.notification.mail.outbox.EmailOutboxStatusHandler;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

@RequiredArgsConstructor
public class GmailOnlyEmailSender implements EmailSender {

    private final EmailSender gmailEmailSender;
    private final EmailOutboxStatusHandler emailOutboxStatusHandler;

    @Override
    @Async
    @CircuitBreaker(name = "primaryEmail", fallbackMethod = "recordFailure")
    public void sendEmails(final List<String> recipientEmails, final String subject, final String body) {
        gmailEmailSender.sendEmails(recipientEmails, subject, body);
    }

    public void recordFailure(
            final List<String> recipientEmails,
            final String subject,
            final String body,
            final RuntimeException cause
    ) {
        for (String recipientEmail : recipientEmails) {
            emailOutboxStatusHandler.handleFailure(recipientEmail, subject, body, cause);
        }
    }
}
