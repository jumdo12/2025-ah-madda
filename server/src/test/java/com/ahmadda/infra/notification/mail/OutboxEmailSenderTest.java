package com.ahmadda.infra.notification.mail;

import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRecipient;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxReferenceType;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRecipientStatus;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxStatus;
import com.ahmadda.infra.notification.mail.outbox.messaging.EmailOutboxEventPublisher;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailOutboxRecipientRepository;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailOutboxRepository;
import com.ahmadda.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.IllegalTransactionStateException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OutboxEmailSenderTest extends IntegrationTest {

    @Autowired
    private OutboxEmailSender sut;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EmailOutboxRecipientRepository emailOutboxRecipientRepository;

    @MockitoBean
    private EmailOutboxEventPublisher emailOutboxEventPublisher;

    @AfterEach
    void tearDown() {
        emailOutboxRecipientRepository.deleteAllInBatch();
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
        assertThatThrownBy(() -> sut.sendEmails(recipients, subject, body))
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
        sut.sendEmails(recipients, subject, body);

        // then
        var savedOutboxes = emailOutboxRepository.findAll();
        var savedRecipients = emailOutboxRecipientRepository.findAll();

        assertSoftly(softly -> {
            softly.assertThat(savedOutboxes)
                    .hasSize(1);
            var outbox = savedOutboxes.get(0);
            softly.assertThat(outbox.getSubject())
                    .isEqualTo(subject);
            softly.assertThat(outbox.getBody())
                    .isEqualTo(body);
            softly.assertThat(outbox.getStatus())
                    .isEqualTo(EmailOutboxStatus.READY);
            softly.assertThat(outbox.getLockedUntil())
                    .isNull();

            softly.assertThat(savedRecipients)
                    .hasSize(2);
            softly.assertThat(savedRecipients)
                    .extracting(EmailOutboxRecipient::getRecipientEmail)
                    .containsExactlyInAnyOrder("a@test.com", "b@test.com");
            softly.assertThat(savedRecipients)
                    .extracting(EmailOutboxRecipient::getStatus)
                    .containsExactlyInAnyOrder(
                            EmailOutboxRecipientStatus.READY,
                            EmailOutboxRecipientStatus.READY
                    );
            softly.assertThat(savedRecipients)
                    .extracting(EmailOutboxRecipient::getAttemptCount)
                    .containsExactlyInAnyOrder(0, 0);
        });
    }

    @Test
    void 이벤트_메일은_아웃박스에_이벤트_참조를_저장한다() {
        // given
        var recipients = List.of("event@test.com");
        var subject = "event subject";
        var body = "event body";
        var eventId = 10L;

        // when
        sut.sendEventEmails(recipients, subject, body, eventId);

        // then
        var savedOutbox = emailOutboxRepository.findAll()
                .get(0);
        assertSoftly(softly -> {
            softly.assertThat(savedOutbox.getReferenceType())
                    .isEqualTo(EmailOutboxReferenceType.EVENT);
            softly.assertThat(savedOutbox.getReferenceId())
                    .isEqualTo(eventId);
        });
    }

    @Test
    void 커밋_후에도_실제_전송은_실행되지_않는다() {
        // given
        var recipients = List.of("c@test.com", "d@test.com");
        var subject = "title";
        var body = "body";

        // when
        sut.sendEmails(recipients, subject, body);
        Long emailOutboxId = emailOutboxRepository.findAll()
                .get(0)
                .getId();

        // then
        verify(emailSender, never()).sendEmails(anyList(), anyString(), anyString());
        verify(emailOutboxEventPublisher, never()).publishCreated(anyLong());

        // 커밋 후 발송은 worker가 담당한다.
        TestTransaction.flagForCommit();
        TestTransaction.end();

        verify(emailSender, never()).sendEmails(anyList(), anyString(), anyString());
        verify(emailOutboxEventPublisher).publishCreated(emailOutboxId);

        emailOutboxRecipientRepository.deleteAllInBatch();
        emailOutboxRepository.deleteAllInBatch();

        if (!TestTransaction.isActive()) {
            TestTransaction.start();
        }
    }
}
