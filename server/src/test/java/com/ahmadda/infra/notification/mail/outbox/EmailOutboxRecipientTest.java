package com.ahmadda.infra.notification.mail.outbox;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class EmailOutboxRecipientTest {

    @Test
    void 레이트_리밋_대기는_시도횟수를_증가시키지_않고_다음_시도시각을_기록한다() {
        // given
        var outbox = EmailOutbox.createReady("제목", "본문", LocalDateTime.now());
        var recipient = EmailOutboxRecipient.create(outbox, "rate-limit@test.com");
        var nextAttemptAt = LocalDateTime.now()
                .plusMinutes(10);

        // when
        recipient.markRateLimitWaiting(nextAttemptAt, "gmail quota exhausted");

        // then
        assertSoftly(softly -> {
            softly.assertThat(recipient.getStatus())
                    .isEqualTo(EmailOutboxRecipientStatus.RATE_LIMIT_WAITING);
            softly.assertThat(recipient.getAttemptCount())
                    .isZero();
            softly.assertThat(recipient.getNextAttemptAt())
                    .isEqualTo(nextAttemptAt);
            softly.assertThat(recipient.getSentAt())
                    .isNull();
            softly.assertThat(recipient.getFailedAt())
                    .isNull();
            softly.assertThat(recipient.getLastErrorMessage())
                    .isEqualTo("gmail quota exhausted");
            softly.assertThat(recipient.isPending())
                    .isTrue();
        });
    }
}
