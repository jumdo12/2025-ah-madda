package com.ahmadda.infra.notification.mail.outbox.messaging;

import com.ahmadda.infra.notification.mail.outbox.worker.EmailOutboxClaimService;
import com.ahmadda.infra.notification.mail.outbox.worker.EmailOutboxDispatcher;
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
        Long emailOutboxId = parseEmailOutboxIdOrDeadLetter(emailOutboxIdMessage);
        if (emailOutboxId == null) {
            return;
        }

        emailOutboxClaimService.claimDispatchableOutbox(emailOutboxId)
                .ifPresentOrElse(
                        emailOutboxDispatcher::dispatch,
                        () -> log.info("emailOutboxMessageSkipped - emailOutboxId: {}", emailOutboxId)
                );
    }

    private Long parseEmailOutboxIdOrDeadLetter(final String emailOutboxIdMessage) {
        try {
            return Long.parseLong(emailOutboxIdMessage);
        } catch (NumberFormatException e) {
            log.warn("invalidEmailOutboxMessageIgnored - message: {}", emailOutboxIdMessage, e);
            return null;
        }
    }
}
