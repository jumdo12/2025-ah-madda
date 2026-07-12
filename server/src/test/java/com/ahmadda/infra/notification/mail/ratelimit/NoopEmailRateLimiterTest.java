package com.ahmadda.infra.notification.mail.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoopEmailRateLimiterTest {

    @Test
    void 기본_레이트_리미터는_항상_발송을_허용한다() {
        // given
        EmailRateLimiter sut = new NoopEmailRateLimiter();

        // when
        EmailRateLimitResult result = sut.tryConsume();

        // then
        assertThat(result.allowed())
                .isTrue();
    }
}
