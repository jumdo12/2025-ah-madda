package com.ahmadda.infra.notification.mail.config;

import com.ahmadda.infra.notification.config.NotificationProperties;
import com.ahmadda.infra.notification.mail.BccChunkingEmailSender;
import com.ahmadda.infra.notification.mail.EmailOutboxSender;
import com.ahmadda.infra.notification.mail.EmailSender;
import com.ahmadda.infra.notification.mail.FailoverEmailSender;
import com.ahmadda.infra.notification.mail.NoopEmailSender;
import com.ahmadda.infra.notification.mail.OutboxEmailSender;
import com.ahmadda.infra.notification.mail.SmtpEmailSender;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailOutboxRecipientRepository;
import com.ahmadda.infra.notification.mail.outbox.repository.EmailOutboxRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
@EnableConfigurationProperties({NotificationProperties.class, SmtpProperties.class, MailRateLimitProperties.class})
public class MailConfig {

    @Bean
    public EmailOutboxSender outboxEmailSender(
            final EmailOutboxRepository emailOutboxRepository,
            final EmailOutboxRecipientRepository emailOutboxRecipientRepository
    ) {
        return new OutboxEmailSender(
                emailOutboxRepository,
                emailOutboxRecipientRepository
        );
    }

    @Bean
    public EmailSender failoverEmailSender(
            final EmailSender googleSmtpEmailSender,
            final EmailSender awsSmtpEmailSender
    ) {
        EmailSender googleChunked = new BccChunkingEmailSender(googleSmtpEmailSender, 100);
        EmailSender awsChunked = new BccChunkingEmailSender(awsSmtpEmailSender, 50);

        return new FailoverEmailSender(googleChunked, awsChunked);
    }

    @Bean
    public EmailSender googleSmtpEmailSender(
            final SmtpProperties smtpProperties
    ) {
        JavaMailSender sender = createJavaMailSender(smtpProperties.getGoogle());
        return new SmtpEmailSender(sender);
    }

    @Bean
    public EmailSender awsSmtpEmailSender(
            final SmtpProperties smtpProperties
    ) {
        JavaMailSender sender = createJavaMailSender(smtpProperties.getAws());
        return new SmtpEmailSender(sender);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "mail.noop", havingValue = "true")
    public EmailSender noopEmailSender() {
        return new NoopEmailSender();
    }

    private JavaMailSender createJavaMailSender(final SmtpProperties.Account acc) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(acc.getHost());
        sender.setPort(acc.getPort());
        sender.setUsername(acc.getUsername());
        sender.setPassword(acc.getPassword());
        sender.setDefaultEncoding("UTF-8");
        if (acc.getProperties() != null) {
            sender.getJavaMailProperties()
                    .putAll(acc.getProperties());
        }

        return sender;
    }
}
