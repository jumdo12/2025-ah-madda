package com.ahmadda.infra.notification.mail;

import java.util.List;

public interface EmailOutboxSender extends EmailSender {

    void sendEventEmails(
            final List<String> recipientEmails,
            final String subject,
            final String body,
            final Long eventId
    );
}
