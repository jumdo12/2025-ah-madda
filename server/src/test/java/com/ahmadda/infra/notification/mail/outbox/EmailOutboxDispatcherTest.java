package com.ahmadda.infra.notification.mail.outbox;

import com.ahmadda.infra.notification.mail.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class EmailOutboxDispatcherTest {

    private EmailSender emailSender;
    private EmailOutboxStatusHandler emailOutboxStatusHandler;
    private EmailOutboxDispatcher sut;

    @BeforeEach
    void setUp() {
        emailSender = mock(EmailSender.class);
        emailOutboxStatusHandler = mock(EmailOutboxStatusHandler.class);
        sut = new EmailOutboxDispatcher(emailSender, emailOutboxStatusHandler);
    }

    @Test
    void 발송에_성공하면_모든_수신자의_아웃박스를_성공_처리한다() {
        // given
        List<String> recipients = List.of("a@test.com", "b@test.com");
        String subject = "subject";
        String body = "body";

        // when
        sut.dispatch(recipients, subject, body);

        // then
        verify(emailSender).sendEmails(recipients, subject, body);
        verify(emailOutboxStatusHandler).handleSuccess("a@test.com", subject, body);
        verify(emailOutboxStatusHandler).handleSuccess("b@test.com", subject, body);
    }

    @Test
    void 발송에_실패하면_모든_수신자의_아웃박스를_실패_처리하고_예외를_다시_던진다() {
        // given
        List<String> recipients = List.of("a@test.com", "b@test.com");
        String subject = "subject";
        String body = "body";
        RuntimeException exception = new IllegalStateException("mail failed");

        doThrow(exception)
                .when(emailSender)
                .sendEmails(recipients, subject, body);

        // when // then
        assertThatThrownBy(() -> sut.dispatch(recipients, subject, body))
                .isSameAs(exception);

        verify(emailOutboxStatusHandler).handleFailure("a@test.com", subject, body, exception);
        verify(emailOutboxStatusHandler).handleFailure("b@test.com", subject, body, exception);
    }

    @Test
    void 수신자가_없으면_메일과_아웃박스를_처리하지_않는다() {
        // when
        sut.dispatch(List.of(), "subject", "body");

        // then
        verifyNoInteractions(emailSender, emailOutboxStatusHandler);
    }
}
