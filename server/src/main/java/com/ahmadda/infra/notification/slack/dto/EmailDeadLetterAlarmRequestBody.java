package com.ahmadda.infra.notification.slack.dto;

import com.ahmadda.application.dto.EmailDeadLetterAlarmPayload;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record EmailDeadLetterAlarmRequestBody(
        List<Block> blocks,
        String channel
) {

    private record Block(
            String type,
            @JsonProperty("text")
            TextObject textObject
    ) {

    }

    private record TextObject(
            String type,
            String text
    ) {

    }

    public static EmailDeadLetterAlarmRequestBody create(
            final EmailDeadLetterAlarmPayload payload,
            final String channelId
    ) {
        String messageText = String.format(
                """
                        *이메일 DLQ 알림*
                        - *DLQ ID*: %d
                        - *Outbox ID*: %d
                        - *Recipient ID*: %d
                        - *수신자*: %s
                        - *제목*: %s
                        - *사유*: %s
                        - *시도 횟수*: %d
                        - *실패 시각*: %s
                        - *에러*: %s
                        """,
                payload.deadLetterId(),
                payload.emailOutboxId(),
                payload.emailOutboxRecipientId(),
                payload.recipientEmail(),
                payload.subject(),
                payload.reason(),
                payload.attemptCount(),
                payload.failedAt(),
                payload.errorMessage()
        );

        TextObject textObject = new TextObject("mrkdwn", messageText);
        Block mainBlock = new Block("section", textObject);

        return new EmailDeadLetterAlarmRequestBody(List.of(mainBlock), channelId);
    }
}
