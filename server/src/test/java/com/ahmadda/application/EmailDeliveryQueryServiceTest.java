package com.ahmadda.application;

import com.ahmadda.common.exception.NotFoundException;
import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetter;
import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetterReason;
import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetterRepository;
import com.ahmadda.infra.notification.mail.outbox.EmailDeliveryAttempt;
import com.ahmadda.infra.notification.mail.outbox.EmailDeliveryAttemptRepository;
import com.ahmadda.infra.notification.mail.outbox.EmailDeliveryAttemptResult;
import com.ahmadda.infra.notification.mail.outbox.EmailOutbox;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRecipient;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRecipientRepository;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRepository;
import com.ahmadda.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailDeliveryQueryServiceTest extends IntegrationTest {

    @Autowired
    private EmailDeliveryQueryService sut;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EmailOutboxRecipientRepository emailOutboxRecipientRepository;

    @Autowired
    private EmailDeliveryAttemptRepository emailDeliveryAttemptRepository;

    @Autowired
    private EmailDeadLetterRepository emailDeadLetterRepository;

    @Test
    void DLQ_목록은_최신_실패순으로_페이징된다() {
        // given
        LocalDateTime now = LocalDateTime.now();
        createDeadLetter("오래된 실패", "old@test.com", now.minusMinutes(10));
        EmailDeadLetter recent = createDeadLetter("최근 실패", "recent@test.com", now);

        // when
        var result = sut.getDeadLetters(0, 1);

        // then
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .singleElement()
                .extracting(EmailDeadLetter::getId)
                .isEqualTo(recent.getId());
    }

    @Test
    void DLQ_상세를_조회한다() {
        // given
        EmailDeadLetter deadLetter = createDeadLetter("상세 실패", "detail@test.com", LocalDateTime.now());

        // when
        EmailDeadLetter result = sut.getDeadLetter(deadLetter.getId());

        // then
        assertThat(result.getRecipientEmail()).isEqualTo("detail@test.com");
        assertThat(result.getEmailOutbox()
                .getSubject()).isEqualTo("상세 실패");
        assertThat(result.getEmailOutboxRecipient()
                .getLastErrorMessage()).isEqualTo("send failed");
    }

    @Test
    void 존재하지_않는_DLQ_상세를_조회하면_예외가_발생한다() {
        // when // then
        assertThatThrownBy(() -> sut.getDeadLetter(999_999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("존재하지 않는 이메일 DLQ입니다.");
    }

    @Test
    void 아웃박스와_수신자별_발송_시도_이력을_최신순으로_조회한다() {
        // given
        LocalDateTime now = LocalDateTime.now();
        EmailOutbox outbox = emailOutboxRepository.save(EmailOutbox.createReady("시도 이력", "본문", now));
        EmailOutboxRecipient recipient = emailOutboxRecipientRepository.save(
                EmailOutboxRecipient.create(outbox, "attempt@test.com")
        );
        EmailDeliveryAttempt firstAttempt = EmailDeliveryAttempt.create(
                outbox,
                recipient,
                1,
                EmailDeliveryAttemptResult.RETRY_SCHEDULED,
                "first failed",
                now.minusMinutes(1)
        );
        EmailDeliveryAttempt secondAttempt = EmailDeliveryAttempt.create(
                outbox,
                recipient,
                2,
                EmailDeliveryAttemptResult.SUCCESS,
                null,
                now
        );
        emailDeliveryAttemptRepository.saveAll(List.of(firstAttempt, secondAttempt));

        // when
        List<EmailDeliveryAttempt> outboxAttempts = sut.getOutboxAttempts(outbox.getId());
        List<EmailDeliveryAttempt> recipientAttempts = sut.getRecipientAttempts(recipient.getId());

        // then
        assertThat(outboxAttempts)
                .extracting(EmailDeliveryAttempt::getAttemptNumber)
                .containsExactly(2, 1);
        assertThat(recipientAttempts)
                .extracting(EmailDeliveryAttempt::getAttemptNumber)
                .containsExactly(2, 1);
    }

    private EmailDeadLetter createDeadLetter(
            final String subject,
            final String recipientEmail,
            final LocalDateTime failedAt
    ) {
        EmailOutbox outbox = emailOutboxRepository.save(EmailOutbox.createReady(subject, "본문", failedAt.minusHours(1)));
        EmailOutboxRecipient recipient = EmailOutboxRecipient.create(outbox, recipientEmail);
        recipient.markFailed(failedAt, "send failed", 3);
        emailOutboxRecipientRepository.save(recipient);

        return emailDeadLetterRepository.save(EmailDeadLetter.create(
                outbox,
                recipient,
                EmailDeadLetterReason.RETRY_EXHAUSTED,
                "send failed",
                failedAt
        ));
    }
}
