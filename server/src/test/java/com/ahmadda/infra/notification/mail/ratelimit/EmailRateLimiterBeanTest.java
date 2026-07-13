package com.ahmadda.infra.notification.mail.ratelimit;

import com.ahmadda.infra.notification.mail.config.MailRateLimitProperties;
import io.github.bucket4j.mysql.MySQLSelectForUpdateBasedProxyManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EmailRateLimiterBeanTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Bucket4jEmailRateLimiter.class, NoopEmailRateLimiter.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    void 메일_레이트리밋이_켜져있으면_Bucket4j_구현체를_사용한다() {
        contextRunner
                .withPropertyValues("mail.rate-limit.enabled=true")
                .run(context -> assertThat(context.getBean(EmailRateLimiter.class))
                        .isInstanceOf(Bucket4jEmailRateLimiter.class));
    }

    @Test
    void 메일_레이트리밋이_꺼져있으면_Noop_구현체를_사용한다() {
        contextRunner
                .withPropertyValues("mail.rate-limit.enabled=false")
                .run(context -> assertThat(context.getBean(EmailRateLimiter.class))
                        .isInstanceOf(NoopEmailRateLimiter.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MailRateLimitProperties.class)
    static class TestConfig {

        @Bean
        MySQLSelectForUpdateBasedProxyManager<Long> bucketProxyManager() {
            return mock(MySQLSelectForUpdateBasedProxyManager.class);
        }
    }
}
