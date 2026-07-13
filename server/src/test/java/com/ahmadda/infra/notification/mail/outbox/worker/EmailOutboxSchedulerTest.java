package com.ahmadda.infra.notification.mail.outbox.worker;

import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetter;
import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetterReason;
import com.ahmadda.infra.notification.mail.outbox.EmailDeliveryAttempt;
import com.ahmadda.infra.notification.mail.outbox.EmailDeliveryAttemptResult;
import com.ahmadda.infra.notification.mail.outbox.EmailOutbox;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRecipient;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRecipientStatus;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxStatus;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailDeadLetterRepository;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailDeliveryAttemptRepository;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailOutboxRecipientRepository;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailOutboxRepository;
import com.ahmadda.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@TestPropertySource(properties = {
        "mail.worker.enabled=true",
        "mail.rate-limit.enabled=false",
        "smtp.google.host=localhost",
        "smtp.google.port=587",
        "smtp.google.username=test-google-user",
        "smtp.google.password=test-google-password",
        "smtp.aws.host=localhost",
        "smtp.aws.port=587",
        "smtp.aws.username=test-aws-user",
        "smtp.aws.password=test-aws-password"
})
class EmailOutboxSchedulerTest extends IntegrationTest {

    @Autowired
    private EmailOutboxScheduler sut;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EmailOutboxRecipientRepository emailOutboxRecipientRepository;

    @Autowired
    private EmailDeliveryAttemptRepository emailDeliveryAttemptRepository;

    @Autowired
    private EmailDeadLetterRepository emailDeadLetterRepository;

    @Test
    void 수신자가_존재하는_READY_아웃박스는_수신자별로_발송되고_상태와_시도_로그를_남긴다() {
        // given
        var outbox = EmailOutbox.createReady(
                "테스트 제목",
                "본문 내용",
                LocalDateTime.now()
                        .minusMinutes(20)
        );
        emailOutboxRepository.save(outbox);
        var recipients = List.of(
                EmailOutboxRecipient.create(outbox, "a@test.com"),
                EmailOutboxRecipient.create(outbox, "b@test.com")
        );
        emailOutboxRecipientRepository.saveAll(recipients);

        // when
        sut.dispatchReadyEmails();

        // then
        verify(emailSender).sendEmails(
                eq(List.of("a@test.com")),
                eq("테스트 제목"),
                eq("본문 내용")
        );
        verify(emailSender).sendEmails(
                eq(List.of("b@test.com")),
                eq("테스트 제목"),
                eq("본문 내용")
        );
        assertSoftly(softly -> {
            softly.assertThat(emailOutboxRepository.findAll())
                    .singleElement()
                    .extracting(EmailOutbox::getStatus)
                    .isEqualTo(EmailOutboxStatus.SENT);
            softly.assertThat(emailOutboxRecipientRepository.findAll())
                    .extracting(EmailOutboxRecipient::getStatus)
                    .containsExactlyInAnyOrder(EmailOutboxRecipientStatus.SENT, EmailOutboxRecipientStatus.SENT);
            softly.assertThat(emailDeliveryAttemptRepository.findAll())
                    .extracting(EmailDeliveryAttempt::getResult)
                    .containsExactlyInAnyOrder(EmailDeliveryAttemptResult.SUCCESS, EmailDeliveryAttemptResult.SUCCESS);
        });
    }

    @Test
    void 수신자가_없으면_아웃박스는_SENT로_닫힌다() {
        // given
        var outbox = EmailOutbox.createReady(
                "빈 아웃박스",
                "내용 없음",
                LocalDateTime.now()
                        .minusMinutes(20)
        );
        emailOutboxRepository.save(outbox);

        // when
        sut.dispatchReadyEmails();

        // then
        var remaining = emailOutboxRepository.findAll();
        assertSoftly(softly -> softly.assertThat(remaining)
                .singleElement()
                .extracting(EmailOutbox::getStatus)
                .isEqualTo(EmailOutboxStatus.SENT));
    }

    @Test
    void READY와_락이_만료된_PROCESSING_아웃박스만_발송된다() {
        // given
        var expired = EmailOutbox.createProcessing(
                "제목1",
                "본문1",
                LocalDateTime.now()
                        .minusMinutes(10),
                LocalDateTime.now()
                        .minusMinutes(1),
                LocalDateTime.now()
                        .minusMinutes(20)
        );
        var expiredRecipient = EmailOutboxRecipient.create(expired, "expired@test.com");

        var fresh = EmailOutbox.createProcessing(
                "제목2",
                "본문2",
                LocalDateTime.now(),
                LocalDateTime.now()
                        .plusMinutes(5),
                LocalDateTime.now()
        );
        var freshRecipient = EmailOutboxRecipient.create(fresh, "fresh@test.com");

        emailOutboxRepository.saveAll(List.of(expired, fresh));
        emailOutboxRecipientRepository.saveAll(List.of(expiredRecipient, freshRecipient));

        // when
        sut.dispatchReadyEmails();

        // then
        verify(emailSender).sendEmails(
                eq(List.of("expired@test.com")),
                eq("제목1"),
                eq("본문1")
        );
        verify(emailSender, never()).sendEmails(
                eq(List.of("fresh@test.com")),
                eq("제목2"),
                eq("본문2")
        );
    }

    @Test
    void 발송_실패시_수신자는_RETRY_WAITING으로_예약되고_시도_로그를_남긴다() {
        // given
        var outbox = EmailOutbox.createReady(
                "락 갱신 테스트",
                "내용",
                LocalDateTime.now()
                        .minusMinutes(20)
        );
        emailOutboxRepository.save(outbox);
        var recipient = EmailOutboxRecipient.create(outbox, "lock@test.com");
        emailOutboxRecipientRepository.save(recipient);
        doThrow(new RuntimeException("send failed"))
                .when(emailSender)
                .sendEmails(
                        eq(List.of("lock@test.com")),
                        eq("락 갱신 테스트"),
                        eq("내용")
                );

        // when
        sut.dispatchReadyEmails();

        // then
        var updated = emailOutboxRepository.findById(outbox.getId())
                .get();
        var updatedRecipient = emailOutboxRecipientRepository.findAllByEmailOutboxId(outbox.getId())
                .get(0);
        assertSoftly(softly -> {
                softly.assertThat(updated.getStatus())
                        .isEqualTo(EmailOutboxStatus.READY);
                softly.assertThat(updated.getLockedAt())
                        .isNotNull();
                softly.assertThat(updated.getLockedUntil())
                        .isNull();
                softly.assertThat(updatedRecipient.getStatus())
                        .isEqualTo(EmailOutboxRecipientStatus.RETRY_WAITING);
                softly.assertThat(updatedRecipient.getAttemptCount())
                        .isEqualTo(1);
                softly.assertThat(updatedRecipient.getNextAttemptAt())
                        .isAfter(LocalDateTime.now());
                softly.assertThat(emailDeliveryAttemptRepository.findAll())
                        .singleElement()
                        .extracting(EmailDeliveryAttempt::getResult)
                        .isEqualTo(EmailDeliveryAttemptResult.RETRY_SCHEDULED);
        });
    }

    @Test
    void 레이트_리밋_대기_시간이_아직_지나지_않으면_발송하지_않는다() {
        // given
        var outbox = EmailOutbox.createReady(
                "레이트 리밋 대기",
                "내용",
                LocalDateTime.now()
                        .minusMinutes(20)
        );
        emailOutboxRepository.save(outbox);
        var recipient = EmailOutboxRecipient.create(outbox, "waiting@test.com");
        recipient.markRateLimitWaiting(
                LocalDateTime.now()
                        .plusMinutes(10),
                "gmail quota exhausted"
        );
        emailOutboxRecipientRepository.save(recipient);

        // when
        sut.dispatchReadyEmails();

        // then
        verify(emailSender, never()).sendEmails(
                eq(List.of("waiting@test.com")),
                eq("레이트 리밋 대기"),
                eq("내용")
        );
        var updatedRecipient = emailOutboxRecipientRepository.findAllByEmailOutboxId(outbox.getId())
                .get(0);
        assertSoftly(softly -> {
            softly.assertThat(updatedRecipient.getStatus())
                    .isEqualTo(EmailOutboxRecipientStatus.RATE_LIMIT_WAITING);
            softly.assertThat(updatedRecipient.getAttemptCount())
                    .isZero();
            softly.assertThat(emailDeliveryAttemptRepository.findAll())
                    .isEmpty();
        });
    }

    @Test
    void 레이트_리밋_대기_시간이_지나면_다시_발송_대상이_된다() {
        // given
        var outbox = EmailOutbox.createReady(
                "레이트 리밋 재개",
                "내용",
                LocalDateTime.now()
                        .minusMinutes(20)
        );
        emailOutboxRepository.save(outbox);
        var recipient = EmailOutboxRecipient.create(outbox, "resume@test.com");
        recipient.markRateLimitWaiting(
                LocalDateTime.now()
                        .minusMinutes(1),
                "gmail quota exhausted"
        );
        emailOutboxRecipientRepository.save(recipient);

        // when
        sut.dispatchReadyEmails();

        // then
        verify(emailSender).sendEmails(
                eq(List.of("resume@test.com")),
                eq("레이트 리밋 재개"),
                eq("내용")
        );
        var updatedRecipient = emailOutboxRecipientRepository.findAllByEmailOutboxId(outbox.getId())
                .get(0);
        assertSoftly(softly -> {
            softly.assertThat(updatedRecipient.getStatus())
                    .isEqualTo(EmailOutboxRecipientStatus.SENT);
            softly.assertThat(updatedRecipient.getAttemptCount())
                    .isEqualTo(1);
            softly.assertThat(emailDeliveryAttemptRepository.findAll())
                    .singleElement()
                    .extracting(EmailDeliveryAttempt::getResult)
                    .isEqualTo(EmailDeliveryAttemptResult.SUCCESS);
        });
    }

    @Test
    void 참조_이벤트가_존재하지_않으면_발송하지_않고_CANCELLED로_마감한다() {
        // given
        var outbox = EmailOutbox.createReadyEvent(
                "삭제된 이벤트 안내",
                "내용",
                999_999L,
                LocalDateTime.now()
                        .minusMinutes(20)
        );
        emailOutboxRepository.save(outbox);
        var recipient = EmailOutboxRecipient.create(outbox, "deleted-event@test.com");
        var retryWaitingRecipient = EmailOutboxRecipient.create(outbox, "retry-waiting@test.com");
        retryWaitingRecipient.scheduleRetry(LocalDateTime.now()
                .plusMinutes(10), "previous failed", 1);
        emailOutboxRecipientRepository.saveAll(List.of(recipient, retryWaitingRecipient));

        // when
        sut.dispatchReadyEmails();

        // then
        verify(emailSender, never()).sendEmails(
                eq(List.of("deleted-event@test.com")),
                eq("삭제된 이벤트 안내"),
                eq("내용")
        );
        var updated = emailOutboxRepository.findById(outbox.getId())
                .get();
        assertSoftly(softly -> {
            softly.assertThat(updated.getStatus())
                    .isEqualTo(EmailOutboxStatus.CANCELLED);
            softly.assertThat(emailOutboxRecipientRepository.findAllByEmailOutboxId(outbox.getId()))
                    .extracting(EmailOutboxRecipient::getStatus)
                    .containsExactlyInAnyOrder(
                            EmailOutboxRecipientStatus.CANCELLED,
                            EmailOutboxRecipientStatus.CANCELLED
                    );
            softly.assertThat(emailDeliveryAttemptRepository.findAll())
                    .extracting(EmailDeliveryAttempt::getResult)
                    .containsExactlyInAnyOrder(
                            EmailDeliveryAttemptResult.SKIPPED,
                            EmailDeliveryAttemptResult.SKIPPED
                    );
            softly.assertThat(emailDeadLetterRepository.findAll())
                    .isEmpty();
        });
    }

    @Test
    void 재시도_횟수를_초과하면_수신자를_DLQ로_격리한다() {
        // given
        var outbox = EmailOutbox.createReady(
                "DLQ 테스트",
                "내용",
                LocalDateTime.now()
                        .minusMinutes(20)
        );
        emailOutboxRepository.save(outbox);
        var recipient = EmailOutboxRecipient.create(outbox, "dlq@test.com");
        recipient.scheduleRetry(LocalDateTime.now().minusMinutes(1), "previous failed", 2);
        emailOutboxRecipientRepository.save(recipient);
        doThrow(new RuntimeException("send failed again"))
                .when(emailSender)
                .sendEmails(
                        eq(List.of("dlq@test.com")),
                        eq("DLQ 테스트"),
                        eq("내용")
                );

        // when
        sut.dispatchReadyEmails();

        // then
        var updated = emailOutboxRepository.findById(outbox.getId())
                .get();
        var updatedRecipient = emailOutboxRecipientRepository.findAllByEmailOutboxId(outbox.getId())
                .get(0);
        assertSoftly(softly -> {
            softly.assertThat(updated.getStatus())
                    .isEqualTo(EmailOutboxStatus.FAILED);
            softly.assertThat(updatedRecipient.getStatus())
                    .isEqualTo(EmailOutboxRecipientStatus.FAILED);
            softly.assertThat(updatedRecipient.getAttemptCount())
                    .isEqualTo(3);
            softly.assertThat(emailDeliveryAttemptRepository.findAll())
                    .singleElement()
                    .extracting(EmailDeliveryAttempt::getResult)
                    .isEqualTo(EmailDeliveryAttemptResult.DEAD_LETTERED);
            softly.assertThat(emailDeadLetterRepository.findAll())
                    .singleElement()
                    .extracting(EmailDeadLetter::getReason)
                    .isEqualTo(EmailDeadLetterReason.RETRY_EXHAUSTED);
        });
    }

    @Test
    void 일부_수신자는_성공하고_일부_수신자는_DLQ로_격리되면_아웃박스는_PARTIAL_FAILED가_된다() {
        // given
        var outbox = EmailOutbox.createReady(
                "부분 실패 테스트",
                "내용",
                LocalDateTime.now()
                        .minusMinutes(20)
        );
        emailOutboxRepository.save(outbox);
        var successRecipient = EmailOutboxRecipient.create(outbox, "success@test.com");
        var failedRecipient = EmailOutboxRecipient.create(outbox, "failed@test.com");
        failedRecipient.scheduleRetry(LocalDateTime.now().minusMinutes(1), "previous failed", 2);
        emailOutboxRecipientRepository.saveAll(List.of(successRecipient, failedRecipient));
        doThrow(new RuntimeException("send failed again"))
                .when(emailSender)
                .sendEmails(
                        eq(List.of("failed@test.com")),
                        eq("부분 실패 테스트"),
                        eq("내용")
                );

        // when
        sut.dispatchReadyEmails();

        // then
        var updated = emailOutboxRepository.findById(outbox.getId())
                .get();
        assertSoftly(softly -> {
            softly.assertThat(updated.getStatus())
                    .isEqualTo(EmailOutboxStatus.PARTIAL_FAILED);
            softly.assertThat(emailOutboxRecipientRepository.findAllByEmailOutboxId(outbox.getId()))
                    .extracting(EmailOutboxRecipient::getStatus)
                    .containsExactlyInAnyOrder(EmailOutboxRecipientStatus.SENT, EmailOutboxRecipientStatus.FAILED);
            softly.assertThat(emailDeliveryAttemptRepository.findAll())
                    .extracting(EmailDeliveryAttempt::getResult)
                    .containsExactlyInAnyOrder(
                            EmailDeliveryAttemptResult.SUCCESS,
                            EmailDeliveryAttemptResult.DEAD_LETTERED
                    );
            softly.assertThat(emailDeadLetterRepository.findAll())
                    .singleElement()
                    .extracting(EmailDeadLetter::getRecipientEmail)
                    .isEqualTo("failed@test.com");
        });
    }
}
