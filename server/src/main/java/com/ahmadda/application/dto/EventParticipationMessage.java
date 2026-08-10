package com.ahmadda.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record EventParticipationMessage(
        UUID participationRequestId,
        Long eventId,
        Long memberId,
        Long applicationFormVersionId,
        LocalDateTime claimedAt,
        List<AnswerCreateRequest> answers
) {

    public EventParticipationMessage {
        answers = List.copyOf(answers);
    }
}
