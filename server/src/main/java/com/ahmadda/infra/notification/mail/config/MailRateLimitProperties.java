package com.ahmadda.infra.notification.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "mail.rate-limit")
public record MailRateLimitProperties(
        @DefaultValue("true")
        boolean enabled,
        @DefaultValue("450")
        int dailyLimit,
        @DefaultValue("5")
        int minuteLimit,
        @DefaultValue("600")
        int cooldownSeconds
) {

    public MailRateLimitProperties {
        validatePositive(dailyLimit, "일일 이메일 발송 제한은 1 이상이어야 합니다.");
        validatePositive(minuteLimit, "분당 이메일 발송 제한은 1 이상이어야 합니다.");
        validatePositive(cooldownSeconds, "이메일 발송 쿨다운은 1초 이상이어야 합니다.");
    }

    public Duration cooldown() {
        return Duration.ofSeconds(cooldownSeconds);
    }

    private static void validatePositive(final int value, final String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
