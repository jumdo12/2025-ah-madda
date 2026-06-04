package com.ahmadda.infra.notification.mail.adapter;

import com.ahmadda.domain.notification.EmailNotifier;
import com.ahmadda.domain.notification.ReminderEmail;
import com.ahmadda.infra.notification.config.NotificationProperties;
import com.ahmadda.infra.notification.mail.EmailOutboxSender;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;

import java.util.List;

@Component
public class EmailNotifierAdapter implements EmailNotifier {

    private final EmailOutboxSender emailOutboxSender;
    private final TemplateEngine templateEngine;
    private final NotificationProperties notificationProperties;

    public EmailNotifierAdapter(
            @Qualifier("outboxEmailSender") final EmailOutboxSender emailOutboxSender,
            final TemplateEngine templateEngine,
            final NotificationProperties notificationProperties
    ) {
        this.emailOutboxSender = emailOutboxSender;
        this.templateEngine = templateEngine;
        this.notificationProperties = notificationProperties;
    }

    @Override
    public void remind(final ReminderEmail reminderEmail) {
        List<String> recipientEmails = reminderEmail.recipientEmails();
        String subject = reminderEmail.payload()
                .renderSubject();
        String body = reminderEmail.payload()
                .renderBody(templateEngine, notificationProperties.getRedirectUrlPrefix());
        Long eventId = reminderEmail.payload()
                .eventId();

        emailOutboxSender.sendEventEmails(recipientEmails, subject, body, eventId);
    }
}
