package com.ahmadda.infra.notification.mail.ratelimit;

import java.time.Duration;

public record EmailRateLimitResult(
        boolean allowed,
        Duration retryAfter,
        String reason
) {

    private static final String ALLOWED_REASON = "allowed";

    public EmailRateLimitResult {
        if (retryAfter == null) {
            throw new IllegalArgumentException("레이트 리밋 재시도 대기시간이 필요합니다.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("레이트 리밋 거절 사유가 필요합니다.");
        }
        if (!allowed && !retryAfter.isPositive()) {
            throw new IllegalArgumentException("레이트 리밋 재시도 대기시간은 0보다 커야 합니다.");
        }
    }

    public static EmailRateLimitResult allow() {
        return new EmailRateLimitResult(true, Duration.ZERO, ALLOWED_REASON);
    }

    public static EmailRateLimitResult rejected(final Duration retryAfter, final String reason) {
        return new EmailRateLimitResult(false, retryAfter, reason);
    }
}
