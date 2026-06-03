package com.ahmadda.domain.notification;

import com.ahmadda.domain.event.Event;
import com.ahmadda.domain.organization.OrganizationMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class Reminder {

    private final EmailNotifier emailNotifier;

    public ReminderHistory remind(
            final List<OrganizationMember> recipients,
            final Event event,
            final String content
    ) {
        ReminderEmail reminderEmail = ReminderEmail.of(recipients, event, content);
        emailNotifier.remind(reminderEmail);

        return ReminderHistory.createNow(event, content, recipients);
    }
}
