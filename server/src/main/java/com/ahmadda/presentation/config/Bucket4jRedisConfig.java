package com.ahmadda.presentation.config;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

import static io.github.bucket4j.distributed.ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax;

@Configuration
public class Bucket4jRedisConfig {

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> bucketRedisConnection(
            final LettuceConnectionFactory connectionFactory
    ) {
        AbstractRedisClient nativeClient = connectionFactory.getRequiredNativeClient();
        if (!(nativeClient instanceof RedisClient redisClient)) {
            throw new IllegalStateException("Bucket4j Rate Limiter는 Redis 단일 노드 연결을 필요로 합니다.");
        }

        RedisCodec<String, byte[]> codec = RedisCodec.of(
                StringCodec.UTF8,
                ByteArrayCodec.INSTANCE
        );
        return redisClient.connect(codec);
    }

    @Bean
    public ProxyManager<String> bucketProxyManager(
            final StatefulRedisConnection<String, byte[]> bucketRedisConnection
    ) {
        return Bucket4jLettuce
                .casBasedBuilder(bucketRedisConnection)
                .expirationAfterWrite(basedOnTimeForRefillingBucketUpToMax(Duration.ofHours(2)))
                .build();
    }

    /**
     * 사용자 단위 요청 제한 정책.
     * <p>
     * short-term(10분)은 순간 폭주를 제어하여 서비스의 안정성을 확보하고,
     * 우발적인 요청 증가를 고려해 자연스럽게 회복되도록 한다.
     * <p>
     * long-term(1시간)은 부하테스트 수준의 지속 요청을 감지해,
     * 지정된 주기 동안 완전 차단(Full Block)하여 시스템을 보호한다.
     */
    @Bean
    public BucketConfiguration memberRateLimitConfig() {
        return BucketConfiguration.builder()
                .addLimit(limit -> limit
                        .capacity(300)
                        .refillGreedy(300, Duration.ofMinutes(10))
                )
                .addLimit(limit -> limit
                        .capacity(1500)
                        .refillIntervally(1500, Duration.ofHours(1))
                )
                .build();
    }
}
