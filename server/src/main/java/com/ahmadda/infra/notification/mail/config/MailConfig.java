package com.ahmadda.infra.notification.mail.config;

import com.ahmadda.infra.notification.config.NotificationProperties;
import com.ahmadda.infra.notification.mail.BccChunkingEmailSender;
import com.ahmadda.infra.notification.mail.EmailSender;
import com.ahmadda.infra.notification.mail.FakeEmailSender;
import com.ahmadda.infra.notification.mail.FailoverEmailSender;
import com.ahmadda.infra.notification.mail.NoopEmailSender;
import com.ahmadda.infra.notification.mail.OutboxEmailSender;
import com.ahmadda.infra.notification.mail.RetryableEmailSender;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxDispatcher;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRepository;
import com.ahmadda.infra.notification.mail.outbox.EmailOutboxStatusHandler;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class MailConfig {

    @Bean
    public EmailSender outboxEmailSender(
            final EmailOutboxRepository emailOutboxRepository,
            final EmailOutboxDispatcher emailOutboxDispatcher
    ) {
        return new OutboxEmailSender(emailOutboxRepository, emailOutboxDispatcher);
    }

    @Bean
    public EmailOutboxDispatcher emailOutboxDispatcher(
            @Qualifier("failoverEmailSender") final EmailSender failoverEmailSender,
            final EmailOutboxStatusHandler emailOutboxStatusHandler
    ) {
        return new EmailOutboxDispatcher(failoverEmailSender, emailOutboxStatusHandler);
    }

    @Bean
    public EmailSender failoverEmailSender(
            final RetryRegistry retryRegistry,
            @Qualifier("googleSmtpEmailSender") final EmailSender googleSmtpEmailSender,
            @Qualifier("awsSmtpEmailSender") final EmailSender awsSmtpEmailSender
    ) {
        EmailSender googleRetryable =
                new RetryableEmailSender(googleSmtpEmailSender, retryRegistry, "googleEmail", 2, 1000);
        EmailSender awsRetryable =
                new RetryableEmailSender(awsSmtpEmailSender, retryRegistry, "awsEmail", 3, 1000);

        EmailSender googleChunked = new BccChunkingEmailSender(googleRetryable, 100);
        EmailSender awsChunked = new BccChunkingEmailSender(awsRetryable, 50);

        return new FailoverEmailSender(googleChunked, awsChunked);
    }

    @Bean
    public EmailSender googleSmtpEmailSender() {
        return new FakeEmailSender();
    }

    @Bean
    public EmailSender awsSmtpEmailSender() {
        return new FakeEmailSender();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "mail.noop", havingValue = "true")
    public EmailSender noopEmailSender() {
        return new NoopEmailSender();
    }
}
