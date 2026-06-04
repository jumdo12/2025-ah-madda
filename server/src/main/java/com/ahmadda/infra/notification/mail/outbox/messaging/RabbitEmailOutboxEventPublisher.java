package com.ahmadda.infra.notification.mail.outbox.messaging;

import com.ahmadda.infra.notification.mail.config.EmailOutboxRabbitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mail.outbox.rabbitmq", name = "enabled", havingValue = "true")
public class RabbitEmailOutboxEventPublisher implements EmailOutboxEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final EmailOutboxRabbitProperties properties;

    @Override
    public void publishCreated(final Long emailOutboxId) {
        rabbitTemplate.convertAndSend(
                properties.exchange(),
                properties.routingKey(),
                String.valueOf(emailOutboxId)
        );
    }

    @Override
    public void publishRetry(final Long emailOutboxId) {
        rabbitTemplate.convertAndSend(
                properties.exchange(),
                properties.retryRoutingKey(),
                String.valueOf(emailOutboxId)
        );
    }
}
