package com.ahmadda.infra.notification.mail.ratelimit;

public interface EmailRateLimiter {

    EmailRateLimitResult tryConsume();
}
