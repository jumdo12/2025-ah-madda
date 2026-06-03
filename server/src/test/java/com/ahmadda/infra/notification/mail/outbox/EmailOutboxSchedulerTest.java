package com.ahmadda.infra.notification.mail.outbox;

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

@TestPropertySource(properties = "mail.worker.enabled=true")
class EmailOutboxSchedulerTest extends IntegrationTest {

    @Autowired
    private EmailOutboxScheduler sut;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EmailOutboxRecipientRepository emailOutboxRecipientRepository;

    @Test
    void 수신자가_존재하는_READY_아웃박스는_발송된다() {
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
                eq(List.of("a@test.com", "b@test.com")),
                eq("테스트 제목"),
                eq("본문 내용")
        );
        assertSoftly(softly -> {
            softly.assertThat(emailOutboxRepository.findAll())
                    .singleElement()
                    .extracting(EmailOutbox::getStatus)
                    .isEqualTo(EmailOutboxStatus.SENT);
            softly.assertThat(emailOutboxRecipientRepository.findAll())
                    .isEmpty();
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
    void 발송_시도한_아웃박스는_PROCESSING으로_claim된다() {
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
        assertSoftly(softly -> {
                softly.assertThat(updated.getStatus())
                        .isEqualTo(EmailOutboxStatus.PROCESSING);
                softly.assertThat(updated.getLockedAt())
                        .isNotNull();
                softly.assertThat(updated.getLockedUntil())
                        .isAfter(LocalDateTime.now());
        });
    }
}
