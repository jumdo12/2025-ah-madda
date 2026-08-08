package com.ahmadda.infra.notification.mail;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class FakeEmailSender implements EmailSender {

    private static final long DEFAULT_SEND_DELAY_MILLIS = 1000L;

    private final long sendDelayMillis;

    public FakeEmailSender() {
        this(DEFAULT_SEND_DELAY_MILLIS);
    }

    FakeEmailSender(final long sendDelayMillis) {
        this.sendDelayMillis = sendDelayMillis;
    }

    @Override
    public void sendEmails(final List<String> recipientEmails, final String subject, final String body) {
        if (recipientEmails.isEmpty()) {
            return;
        }

        sleep();

        log.info("[Fake Email] sent to {} recipients. subject: {}", recipientEmails.size(), subject);
    }

    private void sleep() {
        try {
            Thread.sleep(sendDelayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread()
                    .interrupt();
            throw new IllegalStateException("Fake email sending was interrupted.", e);
        }
    }
}
