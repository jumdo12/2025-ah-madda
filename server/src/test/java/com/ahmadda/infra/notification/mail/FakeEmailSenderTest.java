package com.ahmadda.infra.notification.mail;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;

class FakeEmailSenderTest {

    @Test
    void 가짜_메일을_발송한다() {
        // given
        FakeEmailSender sut = new FakeEmailSender(0L);
        List<String> recipients = List.of("a@test.com", "b@test.com", "c@test.com");
        String subject = "subject";
        String body = "body";

        // when // then
        assertThatNoException()
                .isThrownBy(() -> sut.sendEmails(recipients, subject, body));
    }

    @Test
    void 수신자가_없으면_아무것도_하지_않는다() {
        // given
        FakeEmailSender sut = new FakeEmailSender(0L);

        // when // then
        assertThatNoException()
                .isThrownBy(() -> sut.sendEmails(List.of(), "subject", "body"));
    }
}
