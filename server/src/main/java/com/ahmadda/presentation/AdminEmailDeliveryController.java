package com.ahmadda.presentation;

import com.ahmadda.application.EmailDeliveryQueryService;
import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetter;
import com.ahmadda.infra.notification.mail.outbox.EmailDeliveryAttempt;
import com.ahmadda.presentation.dto.EmailDeadLetterDetailResponse;
import com.ahmadda.presentation.dto.EmailDeadLetterPageResponse;
import com.ahmadda.presentation.dto.EmailDeliveryAttemptResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Email Delivery", description = "이메일 발송 운영 조회 API")
@RestController
@RequestMapping("/api/admin/email")
@RequiredArgsConstructor
public class AdminEmailDeliveryController {

    private final EmailDeliveryQueryService emailDeliveryQueryService;

    @Operation(summary = "이메일 DLQ 목록 조회")
    @GetMapping("/dead-letters")
    public ResponseEntity<EmailDeadLetterPageResponse> getDeadLetters(
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size
    ) {
        Page<EmailDeadLetter> deadLetters = emailDeliveryQueryService.getDeadLetters(page, size);

        return ResponseEntity.ok(EmailDeadLetterPageResponse.from(deadLetters));
    }

    @Operation(summary = "이메일 DLQ 상세 조회")
    @GetMapping("/dead-letters/{deadLetterId}")
    public ResponseEntity<EmailDeadLetterDetailResponse> getDeadLetter(
            @PathVariable final Long deadLetterId
    ) {
        EmailDeadLetter deadLetter = emailDeliveryQueryService.getDeadLetter(deadLetterId);

        return ResponseEntity.ok(EmailDeadLetterDetailResponse.from(deadLetter));
    }

    @Operation(summary = "아웃박스별 이메일 발송 시도 이력 조회")
    @GetMapping("/outboxes/{outboxId}/attempts")
    public ResponseEntity<List<EmailDeliveryAttemptResponse>> getOutboxAttempts(
            @PathVariable final Long outboxId
    ) {
        List<EmailDeliveryAttempt> attempts = emailDeliveryQueryService.getOutboxAttempts(outboxId);
        List<EmailDeliveryAttemptResponse> response = attempts.stream()
                .map(EmailDeliveryAttemptResponse::from)
                .toList();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "수신자별 이메일 발송 시도 이력 조회")
    @GetMapping("/recipients/{recipientId}/attempts")
    public ResponseEntity<List<EmailDeliveryAttemptResponse>> getRecipientAttempts(
            @PathVariable final Long recipientId
    ) {
        List<EmailDeliveryAttempt> attempts = emailDeliveryQueryService.getRecipientAttempts(recipientId);
        List<EmailDeliveryAttemptResponse> response = attempts.stream()
                .map(EmailDeliveryAttemptResponse::from)
                .toList();

        return ResponseEntity.ok(response);
    }
}
