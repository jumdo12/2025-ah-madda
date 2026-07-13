package com.ahmadda.infra.notification.mail.ratelimit;

import com.ahmadda.infra.notification.mail.config.MailRateLimitProperties;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.mysql.MySQLSelectForUpdateBasedProxyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Bucket4jEmailRateLimiterTest {

    private MySQLSelectForUpdateBasedProxyManager<Long> bucketProxyManager;
    private BucketProxy bucket;
    private ConsumptionProbe probe;
    private Bucket4jEmailRateLimiter sut;

    @BeforeEach
    void setUp() {
        bucketProxyManager = mock(MySQLSelectForUpdateBasedProxyManager.class);
        bucket = mock(BucketProxy.class);
        probe = mock(ConsumptionProbe.class);

        var properties = new MailRateLimitProperties(
                true,
                450,
                5,
                600
        );
        sut = new Bucket4jEmailRateLimiter(bucketProxyManager, properties);

        when(bucketProxyManager.getProxy(eq(Bucket4jEmailRateLimiter.GMAIL_EMAIL_BUCKET_KEY), any()))
                .thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1))
                .thenReturn(probe);
    }

    @Test
    void 토큰이_있으면_발송을_허용한다() {
        // given
        when(probe.isConsumed())
                .thenReturn(true);

        // when
        EmailRateLimitResult result = sut.tryConsume();

        // then
        assertSoftly(softly -> {
            softly.assertThat(result.allowed())
                    .isTrue();
            softly.assertThat(result.retryAfter())
                    .isEqualTo(Duration.ZERO);
            softly.assertThat(result.reason())
                    .isEqualTo("allowed");
        });
        verify(bucket).tryConsumeAndReturnRemaining(1);
    }

    @Test
    void 토큰이_없으면_재시도_대기시간과_사유를_반환한다() {
        // given
        when(probe.isConsumed())
                .thenReturn(false);
        when(probe.getNanosToWaitForRefill())
                .thenReturn(TimeUnit.SECONDS.toNanos(30));

        // when
        EmailRateLimitResult result = sut.tryConsume();

        // then
        assertSoftly(softly -> {
            softly.assertThat(result.allowed())
                    .isFalse();
            softly.assertThat(result.retryAfter())
                    .isEqualTo(Duration.ofSeconds(30));
            softly.assertThat(result.reason())
                    .isEqualTo("gmail email rate limit exceeded");
        });
    }

    @Test
    void 재시도_대기시간은_최소_1초로_반환한다() {
        // given
        when(probe.isConsumed())
                .thenReturn(false);
        when(probe.getNanosToWaitForRefill())
                .thenReturn(1L);

        // when
        EmailRateLimitResult result = sut.tryConsume();

        // then
        assertSoftly(softly -> {
            softly.assertThat(result.allowed())
                    .isFalse();
            softly.assertThat(result.retryAfter())
                    .isEqualTo(Duration.ofSeconds(1));
        });
    }
}
