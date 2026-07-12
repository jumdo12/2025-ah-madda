package com.ahmadda.infra.notification.mail.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class MailRateLimitPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void 기본_메일_레이트리밋_설정이_바인딩된다() {
        contextRunner.run(context -> {
            var properties = context.getBean(MailRateLimitProperties.class);

            assertSoftly(softly -> {
                softly.assertThat(properties.enabled())
                        .isTrue();
                softly.assertThat(properties.dailyLimit())
                        .isEqualTo(450);
                softly.assertThat(properties.minuteLimit())
                        .isEqualTo(5);
                softly.assertThat(properties.cooldownSeconds())
                        .isEqualTo(600);
                softly.assertThat(properties.cooldown())
                        .isEqualTo(Duration.ofMinutes(10));
            });
        });
    }

    @Test
    void 메일_레이트리밋_설정을_외부_값으로_덮어쓴다() {
        contextRunner
                .withPropertyValues(
                        "mail.rate-limit.enabled=false",
                        "mail.rate-limit.daily-limit=5",
                        "mail.rate-limit.minute-limit=2",
                        "mail.rate-limit.cooldown-seconds=30"
                )
                .run(context -> {
                    var properties = context.getBean(MailRateLimitProperties.class);

                    assertSoftly(softly -> {
                        softly.assertThat(properties.enabled())
                                .isFalse();
                        softly.assertThat(properties.dailyLimit())
                                .isEqualTo(5);
                        softly.assertThat(properties.minuteLimit())
                                .isEqualTo(2);
                        softly.assertThat(properties.cooldownSeconds())
                                .isEqualTo(30);
                        softly.assertThat(properties.cooldown())
                                .isEqualTo(Duration.ofSeconds(30));
                    });
                });
    }

    @Test
    void 양수가_아닌_메일_레이트리밋_설정은_거부한다() {
        contextRunner
                .withPropertyValues("mail.rate-limit.daily-limit=0")
                .run(context -> assertThat(context)
                        .hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MailRateLimitProperties.class)
    static class TestConfig {
    }
}
