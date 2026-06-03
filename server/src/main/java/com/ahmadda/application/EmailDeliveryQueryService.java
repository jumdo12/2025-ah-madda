package com.ahmadda.application;

import com.ahmadda.common.exception.NotFoundException;
import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetter;
import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetterRepository;
import com.ahmadda.infra.notification.mail.outbox.EmailDeliveryAttempt;
import com.ahmadda.infra.notification.mail.outbox.EmailDeliveryAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailDeliveryQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final EmailDeadLetterRepository emailDeadLetterRepository;
    private final EmailDeliveryAttemptRepository emailDeliveryAttemptRepository;

    @Transactional(readOnly = true)
    public Page<EmailDeadLetter> getDeadLetters(final int page, final int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        return emailDeadLetterRepository.findAllByOrderByFailedAtDescIdDesc(pageable);
    }

    @Transactional(readOnly = true)
    public EmailDeadLetter getDeadLetter(final Long deadLetterId) {
        return emailDeadLetterRepository.findWithAssociationsById(deadLetterId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 이메일 DLQ입니다."));
    }

    @Transactional(readOnly = true)
    public List<EmailDeliveryAttempt> getOutboxAttempts(final Long outboxId) {
        return emailDeliveryAttemptRepository.findAllByEmailOutboxIdOrderByAttemptedAtDescIdDesc(outboxId);
    }

    @Transactional(readOnly = true)
    public List<EmailDeliveryAttempt> getRecipientAttempts(final Long recipientId) {
        return emailDeliveryAttemptRepository.findAllByEmailOutboxRecipientIdOrderByAttemptedAtDescIdDesc(recipientId);
    }
}
