package com.ahmadda.presentation.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.bucket4j.Bucket;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MemberRateLimitProperties.class)
public class Bucket4jInMemoryConfig {

    @Bean
    public LoadingCache<Long, Bucket> memberRateLimitBuckets(
            final MemberRateLimitProperties properties
    ) {
        return Caffeine.newBuilder()
                .maximumSize(properties.getMaximumCacheSize())
                .expireAfterAccess(properties.getExpireAfterAccess())
                .build(memberId -> Bucket.builder()
                        .addLimit(limit -> limit
                                .capacity(properties.getCapacity())
                                .refillGreedy(
                                        properties.getCapacity(),
                                        properties.getRefillPeriod()
                                )
                        )
                        .build()
                );
    }
}
