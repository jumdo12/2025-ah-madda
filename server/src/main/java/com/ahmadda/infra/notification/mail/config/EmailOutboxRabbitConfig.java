package com.ahmadda.infra.notification.mail.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
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
        return new Queue(properties.queue(), true);
    }

    @Bean
    public Binding emailOutboxBinding(
            final Queue emailOutboxQueue,
            final DirectExchange emailOutboxExchange,
            final EmailOutboxRabbitProperties properties
    ) {
        return BindingBuilder.bind(emailOutboxQueue)
                .to(emailOutboxExchange)
                .with(properties.routingKey());
    }
}
