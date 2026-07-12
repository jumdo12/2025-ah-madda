package com.ahmadda.infra.notification.mail.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class EmailRateLimitResultTest {

    @Test
    void 허용_결과는_재시도_대기시간이_없다() {
        // when
        EmailRateLimitResult result = EmailRateLimitResult.allow();

        // then
        assertSoftly(softly -> {
            softly.assertThat(result.allowed())
                    .isTrue();
            softly.assertThat(result.retryAfter())
                    .isEqualTo(Duration.ZERO);
            softly.assertThat(result.reason())
                    .isEqualTo("allowed");
        });
    }

    @Test
    void 거절_결과는_재시도_대기시간과_사유를_가진다() {
        // given
        Duration retryAfter = Duration.ofMinutes(1);

        // when
        EmailRateLimitResult result = EmailRateLimitResult.rejected(retryAfter, "minute quota exhausted");

        // then
        assertSoftly(softly -> {
            softly.assertThat(result.allowed())
                    .isFalse();
            softly.assertThat(result.retryAfter())
                    .isEqualTo(retryAfter);
            softly.assertThat(result.reason())
                    .isEqualTo("minute quota exhausted");
        });
    }

    @Test
    void 거절_결과는_양수_대기시간이_필요하다() {
        assertThatThrownBy(() -> EmailRateLimitResult.rejected(Duration.ZERO, "minute quota exhausted"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("레이트 리밋 재시도 대기시간은 0보다 커야 합니다.");
    }

    @Test
    void 거절_결과는_사유가_필요하다() {
        assertThatThrownBy(() -> EmailRateLimitResult.rejected(Duration.ofMinutes(1), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("레이트 리밋 거절 사유가 필요합니다.");
    }
}
