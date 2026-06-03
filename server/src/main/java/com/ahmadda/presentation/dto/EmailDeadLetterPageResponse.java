package com.ahmadda.presentation.dto;

import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetter;
import org.springframework.data.domain.Page;

import java.util.List;

public record EmailDeadLetterPageResponse(
        List<EmailDeadLetterSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static EmailDeadLetterPageResponse from(final Page<EmailDeadLetter> deadLetters) {
        List<EmailDeadLetterSummaryResponse> items = deadLetters.stream()
                .map(EmailDeadLetterSummaryResponse::from)
                .toList();

        return new EmailDeadLetterPageResponse(
                items,
                deadLetters.getNumber(),
                deadLetters.getSize(),
                deadLetters.getTotalElements(),
                deadLetters.getTotalPages()
        );
    }
}
