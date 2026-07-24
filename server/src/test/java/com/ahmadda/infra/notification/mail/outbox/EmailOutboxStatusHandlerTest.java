package com.ahmadda.infra.notification.mail.outbox;

import com.ahmadda.domain.notification.EmailDeliveryStatus;
import com.ahmadda.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class EmailOutboxStatusHandlerTest extends IntegrationTest {

    @Autowired
    private EmailOutboxStatusHandler sut;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Test
    void 발송에_성공하면_이력을_삭제하지_않고_SENT로_변경한다() {
        // given
        var subject = "아맞다 이벤트 안내";
        var body = "이벤트에 참여해주셔서 감사합니다.";
        var outbox = emailOutboxRepository.save(
                EmailOutbox.createNow(1L, "user@email.com", subject, body)
        );

        // when
        sut.handleSuccess("user@email.com", subject, body);

        // then
        var updated = emailOutboxRepository.findById(outbox.getId())
                .orElseThrow();
        assertSoftly(softly -> {
            softly.assertThat(updated.getStatus())
                    .isEqualTo(EmailDeliveryStatus.SENT);
            softly.assertThat(updated.getSentAt())
                    .isNotNull();
            softly.assertThat(emailOutboxRepository.findById(outbox.getId()))
                    .isPresent();
        });
    }

    @Test
    void 발송에_실패하면_이력을_FAILED로_변경하고_실패_사유를_보존한다() {
        // given
        var subject = "아맞다 이벤트 안내";
        var body = "이벤트에 참여해주셔서 감사합니다.";
        var outbox = emailOutboxRepository.save(
                EmailOutbox.createNow(1L, "user@email.com", subject, body)
        );

        // when
        sut.handleFailure(
                "user@email.com",
                subject,
                body,
                new IllegalStateException("SMTP 발송 실패")
        );

        // then
        var updated = emailOutboxRepository.findById(outbox.getId())
                .orElseThrow();
        assertSoftly(softly -> {
            softly.assertThat(updated.getStatus())
                    .isEqualTo(EmailDeliveryStatus.FAILED);
            softly.assertThat(updated.getFailedAt())
                    .isNotNull();
            softly.assertThat(updated.getFailureReason())
                    .isEqualTo("SMTP 발송 실패");
        });
    }

    @Test
    void 이미_SENT인_이력은_후속_실패로_FAILED가_되지_않는다() {
        // given
        var subject = "아맞다 이벤트 안내";
        var body = "이벤트에 참여해주셔서 감사합니다.";
        var outbox = emailOutboxRepository.save(
                EmailOutbox.createNow(1L, "user@email.com", subject, body)
        );
        sut.handleSuccess("user@email.com", subject, body);

        // when
        sut.handleFailure(
                "user@email.com",
                subject,
                body,
                new IllegalStateException("SMTP 발송 실패")
        );

        // then
        var updated = emailOutboxRepository.findById(outbox.getId())
                .orElseThrow();
        assertSoftly(softly -> {
            softly.assertThat(updated.getStatus())
                    .isEqualTo(EmailDeliveryStatus.SENT);
            softly.assertThat(updated.getFailedAt())
                    .isNull();
        });
    }
}
