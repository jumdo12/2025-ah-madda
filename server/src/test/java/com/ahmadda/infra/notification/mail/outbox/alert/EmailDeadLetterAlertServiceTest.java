package com.ahmadda.infra.notification.mail.outbox.alert;

import com.ahmadda.application.dto.EmailDeadLetterAlarmPayload;
import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetter;
import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetterReason;
import com.ahmadda.infra.notification.mail.outbox.EmailOutbox;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRecipient;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailDeadLetterRepository;
import com.ahmadda.infra.notification.slack.SlackAlarm;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailDeadLetterAlertServiceTest {

    private final EmailDeadLetterRepository emailDeadLetterRepository = mock(EmailDeadLetterRepository.class);
    private final SlackAlarm slackAlarm = mock(SlackAlarm.class);
    private final EmailDeadLetterAlertService sut = new EmailDeadLetterAlertService(
            emailDeadLetterRepository,
            slackAlarm
    );

    @Test
    void DLQ를_조회해_slack_알림_payload를_전송한다() {
        // given
        LocalDateTime failedAt = LocalDateTime.now();
        EmailOutbox outbox = EmailOutbox.createReady("제목", "본문", failedAt.minusMinutes(10));
        ReflectionTestUtils.setField(outbox, "id", 1L);
        EmailOutboxRecipient recipient = EmailOutboxRecipient.create(outbox, "dead@test.com");
        ReflectionTestUtils.setField(recipient, "id", 10L);
        recipient.markFailed(failedAt, "send failed", 3);
        EmailDeadLetter deadLetter = EmailDeadLetter.create(
                outbox,
                recipient,
                EmailDeadLetterReason.RETRY_EXHAUSTED,
                "send failed",
                failedAt
        );
        ReflectionTestUtils.setField(deadLetter, "id", 99L);

        when(emailDeadLetterRepository.findWithAssociationsById(99L))
                .thenReturn(Optional.of(deadLetter));

        // when
        sut.alert(99L);

        // then
        ArgumentCaptor<EmailDeadLetterAlarmPayload> captor =
                ArgumentCaptor.forClass(EmailDeadLetterAlarmPayload.class);
        verify(slackAlarm).alarmEmailDeadLetter(captor.capture());
        EmailDeadLetterAlarmPayload payload = captor.getValue();
        assertSoftly(softly -> {
            softly.assertThat(payload.deadLetterId())
                    .isEqualTo(99L);
            softly.assertThat(payload.emailOutboxId())
                    .isEqualTo(1L);
            softly.assertThat(payload.emailOutboxRecipientId())
                    .isEqualTo(10L);
            softly.assertThat(payload.recipientEmail())
                    .isEqualTo("dead@test.com");
            softly.assertThat(payload.subject())
                    .isEqualTo("제목");
            softly.assertThat(payload.reason())
                    .isEqualTo(EmailDeadLetterReason.RETRY_EXHAUSTED);
            softly.assertThat(payload.errorMessage())
                    .isEqualTo("send failed");
            softly.assertThat(payload.attemptCount())
                    .isEqualTo(3);
            softly.assertThat(payload.failedAt())
                    .isEqualTo(failedAt);
        });
    }

    @Test
    void DLQ가_없으면_slack_알림을_전송하지_않는다() {
        // given
        when(emailDeadLetterRepository.findWithAssociationsById(999L))
                .thenReturn(Optional.empty());

        // when
        sut.alert(999L);

        // then
        verify(slackAlarm, never()).alarmEmailDeadLetter(org.mockito.ArgumentMatchers.any());
    }
}
