package com.ahmadda.infra.notification.mail.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mail.rate-limit", name = "enabled", havingValue = "false")
public class NoopEmailRateLimiter implements EmailRateLimiter {

    @Override
    public EmailRateLimitResult tryConsume() {
        return EmailRateLimitResult.allow();
    }
}
