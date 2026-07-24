package com.ahmadda.infra.notification.mail.outbox;

import com.ahmadda.infra.notification.mail.exception.EmailOutboxException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class EmailOutboxStatusHandler {

    private final EmailOutboxRepository emailOutboxRepository;

    @Transactional
    public void handleSuccess(final String recipientEmail, final String subject, final String body) {
        EmailOutbox outbox = getOutbox(recipientEmail, subject, body);

        outbox.markSent(LocalDateTime.now());
    }

    @Transactional
    public void handleFailure(
            final String recipientEmail,
            final String subject,
            final String body,
            final Throwable cause
    ) {
        EmailOutbox outbox = getOutbox(recipientEmail, subject, body);

        outbox.markFailed(LocalDateTime.now(), failureReason(cause));
    }

    private EmailOutbox getOutbox(
            final String recipientEmail,
            final String subject,
            final String body
    ) {
        return emailOutboxRepository
                .findTopByRecipientEmailAndSubjectAndBodyOrderByCreatedAtDesc(recipientEmail, subject, body)
                .orElseThrow(() -> new EmailOutboxException("존재하지 않는 아웃박스입니다."));
    }

    private String failureReason(final Throwable cause) {
        if (cause.getMessage() != null) {
            return cause.getMessage();
        }

        return cause.getClass()
                .getSimpleName();
    }
}
