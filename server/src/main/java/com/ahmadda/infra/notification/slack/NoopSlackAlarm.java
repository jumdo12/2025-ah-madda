package com.ahmadda.infra.notification.slack;

import com.ahmadda.application.dto.EmailDeadLetterAlarmPayload;
import com.ahmadda.application.dto.MemberCreateAlarmPayload;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoopSlackAlarm implements SlackAlarm {

    @Override
    public void alarmMemberCreation(final MemberCreateAlarmPayload memberCreateAlarmPayload) {
        log.info("[Noop Slack] Member: {}", memberCreateAlarmPayload);
    }

    @Override
    public void alarmEmailDeadLetter(final EmailDeadLetterAlarmPayload emailDeadLetterAlarmPayload) {
        log.info("[Noop Slack] Email dead letter: {}", emailDeadLetterAlarmPayload);
    }
}
