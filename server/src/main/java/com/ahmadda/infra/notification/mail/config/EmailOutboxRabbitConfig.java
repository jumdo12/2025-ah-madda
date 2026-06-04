package com.ahmadda.infra.notification.mail.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EmailOutboxRabbitProperties.class)
@ConditionalOnProperty(prefix = "mail.outbox.rabbitmq", name = "enabled", havingValue = "true")
public class EmailOutboxRabbitConfig {

    @Bean
    public DirectExchange emailOutboxExchange(final EmailOutboxRabbitProperties properties) {
        return new DirectExchange(properties.exchange(), true, false);
    }

    @Bean
    public Queue emailOutboxQueue(final EmailOutboxRabbitProperties properties) {
        return QueueBuilder.durable(properties.queue())
                .build();
    }

    @Bean
    public Queue emailOutboxRetryQueue(final EmailOutboxRabbitProperties properties) {
        return QueueBuilder.durable(properties.retryQueue())
                .ttl(properties.retryDelayMillis())
                .deadLetterExchange(properties.exchange())
                .deadLetterRoutingKey(properties.routingKey())
                .build();
    }

    @Bean
    public Queue emailOutboxDeadLetterQueue(final EmailOutboxRabbitProperties properties) {
        return QueueBuilder.durable(properties.deadLetterQueue())
                .build();
    }

    @Bean
    public Binding emailOutboxBinding(
            @Qualifier("emailOutboxQueue") final Queue emailOutboxQueue,
            final DirectExchange emailOutboxExchange,
            final EmailOutboxRabbitProperties properties
    ) {
        return BindingBuilder.bind(emailOutboxQueue)
                .to(emailOutboxExchange)
                .with(properties.routingKey());
    }

    @Bean
    public Binding emailOutboxRetryBinding(
            @Qualifier("emailOutboxRetryQueue") final Queue emailOutboxRetryQueue,
            final DirectExchange emailOutboxExchange,
            final EmailOutboxRabbitProperties properties
    ) {
        return BindingBuilder.bind(emailOutboxRetryQueue)
                .to(emailOutboxExchange)
                .with(properties.retryRoutingKey());
    }

    @Bean
    public Binding emailOutboxDeadLetterBinding(
            @Qualifier("emailOutboxDeadLetterQueue") final Queue emailOutboxDeadLetterQueue,
            final DirectExchange emailOutboxExchange,
            final EmailOutboxRabbitProperties properties
    ) {
        return BindingBuilder.bind(emailOutboxDeadLetterQueue)
                .to(emailOutboxExchange)
                .with(properties.deadLetterRoutingKey());
    }
}
