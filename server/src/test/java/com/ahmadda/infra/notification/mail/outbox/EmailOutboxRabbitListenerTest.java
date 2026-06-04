package com.ahmadda.infra.notification.mail.outbox;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EmailOutboxRabbitListenerTest {

    private final EmailOutboxClaimService emailOutboxClaimService = mock(EmailOutboxClaimService.class);
    private final EmailOutboxDispatcher emailOutboxDispatcher = mock(EmailOutboxDispatcher.class);
    private final EmailOutboxEventPublisher emailOutboxEventPublisher = mock(EmailOutboxEventPublisher.class);
    private final EmailOutboxRabbitListener sut = new EmailOutboxRabbitListener(
            emailOutboxClaimService,
            emailOutboxDispatcher,
            emailOutboxEventPublisher
    );

    @Test
    void 메시지의_아웃박스를_claim한_뒤_발송한다() {
        // given
        when(emailOutboxClaimService.claimDispatchableOutbox(1L))
                .thenReturn(Optional.of(1L));

        // when
        sut.dispatchCreatedOutbox("1");

        // then
        verify(emailOutboxClaimService).claimDispatchableOutbox(1L);
        verify(emailOutboxDispatcher).dispatch(1L);
    }

    @Test
    void 이미_처리중이거나_처리된_아웃박스_메시지는_무시한다() {
        // given
        when(emailOutboxClaimService.claimDispatchableOutbox(2L))
                .thenReturn(Optional.empty());

        // when
        sut.dispatchCreatedOutbox("2");

        // then
        verify(emailOutboxClaimService).claimDispatchableOutbox(2L);
        verify(emailOutboxDispatcher, never()).dispatch(2L);
    }

    @Test
    void 아웃박스_id가_숫자가_아니면_RabbitMQ_DLQ로_격리한다() {
        // when
        sut.dispatchCreatedOutbox("invalid");

        // then
        verify(emailOutboxEventPublisher).publishDeadLetter(
                "invalid",
                "이메일 아웃박스 메시지 형식이 올바르지 않습니다."
        );
        verifyNoInteractions(emailOutboxClaimService, emailOutboxDispatcher);
    }
}
