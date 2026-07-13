package com.ahmadda.infra.notification.mail.ratelimit;

import com.ahmadda.infra.notification.mail.config.MailRateLimitProperties;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.mysql.MySQLSelectForUpdateBasedProxyManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "mail.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class Bucket4jEmailRateLimiter implements EmailRateLimiter {

    static final Long GMAIL_EMAIL_BUCKET_KEY = -10_001L;
    private static final String RATE_LIMIT_EXCEEDED_REASON = "gmail email rate limit exceeded";
    private static final long TOKENS_PER_EMAIL = 1L;

    private final MySQLSelectForUpdateBasedProxyManager<Long> bucketProxyManager;
    private final BucketConfiguration bucketConfiguration;

    public Bucket4jEmailRateLimiter(
            final MySQLSelectForUpdateBasedProxyManager<Long> bucketProxyManager,
            final MailRateLimitProperties mailRateLimitProperties
    ) {
        this.bucketProxyManager = bucketProxyManager;
        this.bucketConfiguration = createBucketConfiguration(mailRateLimitProperties);
    }

    @Override
    public EmailRateLimitResult tryConsume() {
        BucketProxy bucket = bucketProxyManager.getProxy(GMAIL_EMAIL_BUCKET_KEY, () -> bucketConfiguration);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(TOKENS_PER_EMAIL);

        if (probe.isConsumed()) {
            return EmailRateLimitResult.allow();
        }

        return EmailRateLimitResult.rejected(
                retryAfter(probe),
                RATE_LIMIT_EXCEEDED_REASON
        );
    }

    private BucketConfiguration createBucketConfiguration(final MailRateLimitProperties properties) {
        return BucketConfiguration.builder()
                .addLimit(limit -> limit
                        .capacity(properties.minuteLimit())
                        .refillGreedy(properties.minuteLimit(), Duration.ofMinutes(1))
                )
                .addLimit(limit -> limit
                        .capacity(properties.dailyLimit())
                        .refillIntervally(properties.dailyLimit(), Duration.ofDays(1))
                )
                .build();
    }

    private Duration retryAfter(final ConsumptionProbe probe) {
        Duration retryAfter = Duration.ofNanos(probe.getNanosToWaitForRefill());
        if (retryAfter.isPositive()) {
            return retryAfter.compareTo(Duration.ofSeconds(1)) < 0
                    ? Duration.ofSeconds(1)
                    : retryAfter;
        }

        return Duration.ofSeconds(1);
    }
}
