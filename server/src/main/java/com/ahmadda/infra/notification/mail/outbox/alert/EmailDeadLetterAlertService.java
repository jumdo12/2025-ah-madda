package com.ahmadda.infra.notification.mail.outbox.alert;

import com.ahmadda.application.dto.EmailDeadLetterAlarmPayload;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailDeadLetterRepository;
import com.ahmadda.infra.notification.slack.SlackAlarm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailDeadLetterAlertService {

    private final EmailDeadLetterRepository emailDeadLetterRepository;
    private final SlackAlarm slackAlarm;

    public void alert(final Long deadLetterId) {
        emailDeadLetterRepository.findWithAssociationsById(deadLetterId)
                .map(EmailDeadLetterAlarmPayload::from)
                .ifPresentOrElse(
                        slackAlarm::alarmEmailDeadLetter,
                        () -> log.warn("emailDeadLetterAlertSkipped - deadLetterId: {}", deadLetterId)
                );
    }
}
