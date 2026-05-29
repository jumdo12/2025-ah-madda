package com.ahmadda.infra.notification.mail.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class EmailOutboxScheduler {

    private final EmailOutboxDispatcher emailOutboxDispatcher;
    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxRecipientRepository emailOutboxRecipientRepository;

    private static final int SOFT_LOCK_TTL_MINUTES = 5;

    public EmailOutboxScheduler(
            final EmailOutboxDispatcher emailOutboxDispatcher,
            final EmailOutboxRepository emailOutboxRepository,
            final EmailOutboxRecipientRepository emailOutboxRecipientRepository
    ) {
        this.emailOutboxDispatcher = emailOutboxDispatcher;
        this.emailOutboxRepository = emailOutboxRepository;
        this.emailOutboxRecipientRepository = emailOutboxRecipientRepository;
    }

    @Transactional
    @Scheduled(fixedRate = 60 * 1000)
    public void resendFailedEmails() {
        LocalDateTime threshold = LocalDateTime.now()
                .minusMinutes(SOFT_LOCK_TTL_MINUTES);
        List<EmailOutbox> failedOutboxes = emailOutboxRepository.findAndLockExpiredOutboxes(threshold);

        for (EmailOutbox outbox : failedOutboxes) {
            List<EmailOutboxRecipient> recipients =
                    emailOutboxRecipientRepository.findAllByEmailOutboxId(outbox.getId());

            if (recipients.isEmpty()) {
                emailOutboxRepository.delete(outbox);
                continue;
            }

            outbox.lock();

            emailOutboxDispatcher.dispatch(outbox.getId());
        }
    }
}
