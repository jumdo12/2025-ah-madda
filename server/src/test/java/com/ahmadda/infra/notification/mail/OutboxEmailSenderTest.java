package com.ahmadda.infra.notification.mail;

import com.ahmadda.domain.notification.EmailDeliveryStatus;
import com.ahmadda.infra.notification.mail.outbox.EmailOutbox;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRepository;
import com.ahmadda.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.IllegalTransactionStateException;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OutboxEmailSenderTest extends IntegrationTest {

    @Autowired
    private OutboxEmailSender sut;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @AfterEach
    void tearDown() {
        emailOutboxRepository.deleteAllInBatch();
    }

    @Test
    void 트랜잭션이_없으면_예외가_발생한다() {
        // given
        if (TestTransaction.isActive()) {
            TestTransaction.end();
        }

        var recipients = List.of("no@test.com");
        var subject = "subject";
        var body = "body";

        // when // then
        assertThatThrownBy(() -> sut.sendEventEmails(1L, recipients, subject, body))
                .isInstanceOf(IllegalTransactionStateException.class);

        if (!TestTransaction.isActive()) {
            TestTransaction.start();
        }
    }

    @Test
    void 트랜잭션_내에서_아웃박스가_저장된다() {
        // given
        var recipients = List.of("a@test.com", "b@test.com");
        var subject = "subject";
        var body = "body";

        // when
        sut.sendEventEmails(1L, recipients, subject, body);

        // then
        var savedOutboxes = emailOutboxRepository.findAll();

        assertSoftly(softly -> {
            softly.assertThat(savedOutboxes)
                    .hasSize(2);
            softly.assertThat(savedOutboxes)
                    .extracting(EmailOutbox::getRecipientEmail)
                    .containsExactlyInAnyOrder("a@test.com", "b@test.com");
            softly.assertThat(savedOutboxes)
                    .extracting(EmailOutbox::getEventId)
                    .containsOnly(1L);
            softly.assertThat(savedOutboxes)
                    .extracting(EmailOutbox::getSubject)
                    .containsOnly(subject);
            softly.assertThat(savedOutboxes)
                    .extracting(EmailOutbox::getBody)
                    .containsOnly(body);
            softly.assertThat(savedOutboxes)
                    .extracting(EmailOutbox::getStatus)
                    .containsOnly(EmailDeliveryStatus.PENDING);
        });
    }

    @Test
    void 커밋_후에만_실제_전송이_실행된다() {
        // given
        var recipients = List.of("c@test.com", "d@test.com");
        var subject = "title";
        var body = "body";

        // when
        sut.sendEventEmails(1L, recipients, subject, body);

        // then
        verify(emailSender, never()).sendEmails(anyList(), anyString(), anyString());

        // afterCommit 트리거
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // 커밋 후 비동기 dispatcher가 delegate를 호출한다.
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(emailSender, times(1)).sendEmails(recipients, subject, body));
    }
}
