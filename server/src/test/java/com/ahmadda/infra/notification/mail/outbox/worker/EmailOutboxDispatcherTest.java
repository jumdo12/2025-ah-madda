package com.ahmadda.infra.notification.mail.outbox.worker;

import com.ahmadda.infra.notification.mail.EmailSender;
import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetter;
import com.ahmadda.infra.notification.mail.outbox.EmailOutbox;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRecipient;
import com.ahmadda.infra.notification.mail.outbox.alert.EmailDeadLetterAlertService;
import com.ahmadda.infra.notification.mail.outbox.messaging.EmailOutboxEventPublisher;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailDeadLetterRepository;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailDeliveryAttemptRepository;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailOutboxRecipientRepository;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailOutboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailOutboxDispatcherTest {

    private final EmailSender emailSender = mock(EmailSender.class);
    private final EmailOutboxRepository emailOutboxRepository = mock(EmailOutboxRepository.class);
    private final EmailOutboxRecipientRepository emailOutboxRecipientRepository =
            mock(EmailOutboxRecipientRepository.class);
    private final EmailDeliveryAttemptRepository emailDeliveryAttemptRepository =
            mock(EmailDeliveryAttemptRepository.class);
    private final EmailDeadLetterRepository emailDeadLetterRepository = mock(EmailDeadLetterRepository.class);
    private final EmailOutboxReferenceValidator emailOutboxReferenceValidator = mock(EmailOutboxReferenceValidator.class);
    private final EmailOutboxEventPublisher emailOutboxEventPublisher = mock(EmailOutboxEventPublisher.class);
    private final EmailDeadLetterAlertService emailDeadLetterAlertService = mock(EmailDeadLetterAlertService.class);
    private final EmailOutboxDispatcher sut = new EmailOutboxDispatcher(
            emailSender,
            emailOutboxRepository,
            emailOutboxRecipientRepository,
            emailDeliveryAttemptRepository,
            emailDeadLetterRepository,
            emailOutboxReferenceValidator,
            emailOutboxEventPublisher,
            emailDeadLetterAlertService
    );

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 재시도_대상_실패는_커밋_이후_retry_queue_publish를_등록한다() {
        // given
        TransactionSynchronizationManager.initSynchronization();
        var outbox = EmailOutbox.createReady("제목", "본문", LocalDateTime.now()
                .minusMinutes(1));
        ReflectionTestUtils.setField(outbox, "id", 1L);
        var recipient = EmailOutboxRecipient.create(outbox, "retry@test.com");
        ReflectionTestUtils.setField(recipient, "id", 10L);

        when(emailOutboxRepository.findById(1L))
                .thenReturn(Optional.of(outbox));
        when(emailOutboxRecipientRepository.findDispatchableRecipients(eq(1L), any(LocalDateTime.class)))
                .thenReturn(List.of(recipient));
        when(emailOutboxRecipientRepository.findAllByEmailOutboxId(1L))
                .thenReturn(List.of(recipient));
        when(emailOutboxReferenceValidator.canDispatch(outbox))
                .thenReturn(true);
        doThrow(new RuntimeException("send failed"))
                .when(emailSender)
                .sendEmails(List.of("retry@test.com"), "제목", "본문");

        // when
        sut.dispatch(1L);

        // then
        verify(emailOutboxEventPublisher, never()).publishRetry(1L);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(emailOutboxEventPublisher).publishRetry(1L);
    }

    @Test
    void DLQ_저장은_커밋_이후_slack_알림을_등록한다() {
        // given
        TransactionSynchronizationManager.initSynchronization();
        var outbox = EmailOutbox.createReady("제목", "본문", LocalDateTime.now()
                .minusMinutes(1));
        ReflectionTestUtils.setField(outbox, "id", 1L);
        var recipient = EmailOutboxRecipient.create(outbox, "dead@test.com");
        ReflectionTestUtils.setField(recipient, "id", 10L);
        recipient.scheduleRetry(LocalDateTime.now()
                .minusMinutes(1), "previous failed", 2);

        when(emailOutboxRepository.findById(1L))
                .thenReturn(Optional.of(outbox));
        when(emailOutboxRecipientRepository.findDispatchableRecipients(eq(1L), any(LocalDateTime.class)))
                .thenReturn(List.of(recipient));
        when(emailOutboxRecipientRepository.findAllByEmailOutboxId(1L))
                .thenReturn(List.of(recipient));
        when(emailOutboxReferenceValidator.canDispatch(outbox))
                .thenReturn(true);
        when(emailDeadLetterRepository.save(any(EmailDeadLetter.class)))
                .thenAnswer(invocation -> {
                    EmailDeadLetter deadLetter = invocation.getArgument(0);
                    ReflectionTestUtils.setField(deadLetter, "id", 99L);
                    return deadLetter;
                });
        doThrow(new RuntimeException("send failed again"))
                .when(emailSender)
                .sendEmails(List.of("dead@test.com"), "제목", "본문");

        // when
        sut.dispatch(1L);

        // then
        verify(emailDeadLetterAlertService, never()).alert(99L);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(emailDeadLetterAlertService).alert(99L);
        verify(emailOutboxEventPublisher, never()).publishRetry(1L);
    }
}
