package com.ahmadda.infra.notification.mail.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${mail.worker.enabled:false}' == 'true' && '${mail.outbox.rabbitmq.enabled:false}' == 'true'")
public class EmailOutboxRabbitListener {

    private final EmailOutboxClaimService emailOutboxClaimService;
    private final EmailOutboxDispatcher emailOutboxDispatcher;

    @RabbitListener(queues = "${mail.outbox.rabbitmq.queue:email.outbox.dispatch}")
    public void dispatchCreatedOutbox(final String emailOutboxIdMessage) {
        Long emailOutboxId = parseEmailOutboxId(emailOutboxIdMessage);

        emailOutboxClaimService.claimDispatchableOutbox(emailOutboxId)
                .ifPresentOrElse(
                        emailOutboxDispatcher::dispatch,
                        () -> log.info("emailOutboxMessageSkipped - emailOutboxId: {}", emailOutboxId)
                );
    }

    private Long parseEmailOutboxId(final String emailOutboxIdMessage) {
        try {
            return Long.parseLong(emailOutboxIdMessage);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("이메일 아웃박스 메시지 형식이 올바르지 않습니다.", e);
        }
    }
}
