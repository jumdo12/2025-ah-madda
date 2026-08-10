package com.ahmadda.presentation.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@ConfigurationProperties(prefix = "rate-limit.member")
public class MemberRateLimitProperties {

    private final long capacity;
    private final Duration refillPeriod;
    private final long maximumCacheSize;
    private final Duration expireAfterAccess;

    public MemberRateLimitProperties(
            final long capacity,
            final Duration refillPeriod,
            final long maximumCacheSize,
            final Duration expireAfterAccess
    ) {
        validateProperties(capacity, refillPeriod, maximumCacheSize, expireAfterAccess);

        this.capacity = capacity;
        this.refillPeriod = refillPeriod;
        this.maximumCacheSize = maximumCacheSize;
        this.expireAfterAccess = expireAfterAccess;
    }

    private void validateProperties(
            final long capacity,
            final Duration refillPeriod,
            final long maximumCacheSize,
            final Duration expireAfterAccess
    ) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("회원 처리율 제한 용량은 0보다 커야 합니다.");
        }
        if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("회원 처리율 제한 충전 주기는 0보다 커야 합니다.");
        }
        if (maximumCacheSize <= 0) {
            throw new IllegalArgumentException("회원 처리율 제한 캐시 크기는 0보다 커야 합니다.");
        }
        if (expireAfterAccess == null || expireAfterAccess.isZero() || expireAfterAccess.isNegative()) {
            throw new IllegalArgumentException("회원 처리율 제한 캐시 만료 시간은 0보다 커야 합니다.");
        }
    }
}
