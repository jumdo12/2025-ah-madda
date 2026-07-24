package com.ahmadda.infra.notification.mail;

import java.util.List;

public interface EmailSender {

    void sendEmails(final List<String> recipientEmails, final String subject, final String body);

    default void sendEventEmails(
            final Long eventId,
            final List<String> recipientEmails,
            final String subject,
            final String body
    ) {
        sendEmails(recipientEmails, subject, body);
    }
}
