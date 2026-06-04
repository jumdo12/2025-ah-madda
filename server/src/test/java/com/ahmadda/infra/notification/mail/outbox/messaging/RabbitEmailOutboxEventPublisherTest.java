package com.ahmadda.infra.notification.mail.outbox.messaging;

import com.ahmadda.infra.notification.mail.config.EmailOutboxRabbitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitEmailOutboxEventPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final EmailOutboxRabbitProperties properties = new EmailOutboxRabbitProperties(
            true,
            "email.outbox",
            "email.outbox.dispatch",
            "email.dispatch",
            "email.outbox.retry",
            "email.retry",
            300_000,
            "email.outbox.dead-letter",
            "email.dead-letter"
    );
    private final RabbitEmailOutboxEventPublisher sut = new RabbitEmailOutboxEventPublisher(
            rabbitTemplate,
            properties
    );

    @Test
    void 생성_이벤트는_작업_queue_routing_key로_publish한다() {
        // when
        sut.publishCreated(1L);

        // then
        verify(rabbitTemplate).convertAndSend("email.outbox", "email.dispatch", "1");
    }

    @Test
    void 재시도_이벤트는_retry_queue_routing_key로_publish한다() {
        // when
        sut.publishRetry(2L);

        // then
        verify(rabbitTemplate).convertAndSend("email.outbox", "email.retry", "2");
    }

    @Test
    void 잘못된_메시지는_RabbitMQ_DLQ_routing_key로_publish한다() {
        // when
        sut.publishDeadLetter("invalid", "invalid id");

        // then
        verify(rabbitTemplate).convertAndSend(
                eq("email.outbox"),
                eq("email.dead-letter"),
                any(Message.class)
        );
    }
}
