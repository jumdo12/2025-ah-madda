package com.ahmadda.infra.notification.mail.ratelimit;

import org.springframework.stereotype.Component;

@Component
public class NoopEmailRateLimiter implements EmailRateLimiter {

    @Override
    public EmailRateLimitResult tryConsume() {
        return EmailRateLimitResult.allow();
    }
}
