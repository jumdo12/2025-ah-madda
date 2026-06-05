package com.ahmadda.infra.notification.slack;

import com.ahmadda.application.dto.EmailDeadLetterAlarmPayload;
import com.ahmadda.application.dto.MemberCreateAlarmPayload;

public interface SlackAlarm {

    void alarmMemberCreation(final MemberCreateAlarmPayload memberCreateAlarmPayload);

    void alarmEmailDeadLetter(final EmailDeadLetterAlarmPayload emailDeadLetterAlarmPayload);
}
