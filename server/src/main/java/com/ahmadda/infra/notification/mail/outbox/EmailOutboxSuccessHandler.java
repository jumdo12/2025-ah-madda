package com.ahmadda.infra.notification.mail.outbox;

import com.ahmadda.infra.notification.mail.exception.EmailOutboxException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Component
@RequiredArgsConstructor
public class EmailOutboxSuccessHandler {

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxRecipientRepository emailOutboxRecipientRepository;

    @Transactional
    public void handleSuccess(final Long emailOutboxId, final String recipientEmail) {
        int deletedCount = emailOutboxRecipientRepository
                .deleteByEmailOutboxIdAndRecipientEmail(emailOutboxId, recipientEmail);
        if (deletedCount == 0) {
            throw new EmailOutboxException("존재하지 않는 아웃박스 수신자입니다.");
        }

        boolean hasRemaining = emailOutboxRecipientRepository.existsByEmailOutboxId(emailOutboxId);
        if (!hasRemaining) {
            emailOutboxRepository.deleteById(emailOutboxId);
        }
    }
}
