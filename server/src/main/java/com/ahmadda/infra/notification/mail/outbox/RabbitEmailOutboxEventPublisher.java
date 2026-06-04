package com.ahmadda.infra.notification.mail.outbox;

import com.ahmadda.infra.notification.mail.config.EmailOutboxRabbitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

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

    @Override
    public void publishDeadLetter(final String message, final String reason) {
        rabbitTemplate.convertAndSend(
                properties.exchange(),
                properties.deadLetterRoutingKey(),
                MessageBuilder.withBody(message.getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                        .setHeader("error-reason", reason)
                        .build()
        );
    }
}
