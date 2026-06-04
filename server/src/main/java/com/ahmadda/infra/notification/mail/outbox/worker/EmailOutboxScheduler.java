package com.ahmadda.infra.notification.mail.outbox.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "mail.worker", name = "enabled", havingValue = "true")
public class EmailOutboxScheduler {

    private final EmailOutboxDispatcher emailOutboxDispatcher;
    private final EmailOutboxClaimService emailOutboxClaimService;

    public EmailOutboxScheduler(
            final EmailOutboxDispatcher emailOutboxDispatcher,
            final EmailOutboxClaimService emailOutboxClaimService
    ) {
        this.emailOutboxDispatcher = emailOutboxDispatcher;
        this.emailOutboxClaimService = emailOutboxClaimService;
    }

    @Scheduled(fixedDelay = 1000)
    public void dispatchReadyEmails() {
        List<Long> emailOutboxIds = emailOutboxClaimService.claimDispatchableOutboxes();

        for (Long emailOutboxId : emailOutboxIds) {
            emailOutboxDispatcher.dispatch(emailOutboxId);
        }
    }
}
