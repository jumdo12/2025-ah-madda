package com.ahmadda.infra.notification.mail.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EmailOutboxClaimService {

    private static final int LOCK_TTL_MINUTES = 5;

    private final EmailOutboxRepository emailOutboxRepository;

    @Transactional
    public List<Long> claimDispatchableOutboxes() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockedUntil = now.plusMinutes(LOCK_TTL_MINUTES);

        return emailOutboxRepository.findAndLockDispatchableOutboxes(now)
                .stream()
                .peek(outbox -> outbox.processUntil(lockedUntil))
                .map(EmailOutbox::getId)
                .toList();
    }

    @Transactional
    public Optional<Long> claimDispatchableOutbox(final Long emailOutboxId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockedUntil = now.plusMinutes(LOCK_TTL_MINUTES);

        return emailOutboxRepository.findAndLockDispatchableOutboxById(emailOutboxId, now)
                .map(outbox -> {
                    outbox.processUntil(lockedUntil);
                    return outbox.getId();
                });
    }
}
