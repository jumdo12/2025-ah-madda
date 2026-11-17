package com.ahmadda.infra.notification.mail.adapter;

import com.ahmadda.domain.notification.EmailNotifier;
import com.ahmadda.domain.notification.ReminderEmail;
import com.ahmadda.infra.notification.config.NotificationProperties;
import com.ahmadda.infra.notification.mail.EmailSender;
import lombok.RequiredArgsConstructor;
import org.thymeleaf.TemplateEngine;

import java.util.List;

@RequiredArgsConstructor
public class EmailNotifierAdapter implements EmailNotifier {

    private final EmailSender emailSender;
    private final TemplateEngine templateEngine;
    private final NotificationProperties notificationProperties;

    @Override
    public void remind(final ReminderEmail reminderEmail) {
        Long eventId = reminderEmail.payload()
                .body()
                .eventId();
        List<String> recipientEmails = reminderEmail.recipientEmails();
        String subject = reminderEmail.payload()
                .renderSubject();
        String body = reminderEmail.payload()
                .renderBody(templateEngine, notificationProperties.getRedirectUrlPrefix());

        emailSender.sendEventEmails(eventId, recipientEmails, subject, body);
    }
}
