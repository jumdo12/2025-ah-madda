package com.ahmadda.infra.notification.mail.outbox;

import com.ahmadda.domain.notification.EmailDeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailOutbox {

    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_outbox_id")
    private Long id;

    @Column(name = "event_id")
    private Long eventId;

    @Column(nullable = false)
    private String recipientEmail;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailDeliveryStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    private LocalDateTime failedAt;

    @Column(length = MAX_FAILURE_REASON_LENGTH)
    private String failureReason;

    private EmailOutbox(
            final Long eventId,
            final String recipientEmail,
            final String subject,
            final String body,
            final LocalDateTime createdAt
    ) {
        this.eventId = eventId;
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.body = body;
        this.status = EmailDeliveryStatus.PENDING;
        this.createdAt = createdAt;
    }

    public static EmailOutbox createNow(
            final Long eventId,
            final String recipientEmail,
            final String subject,
            final String body
    ) {
        return new EmailOutbox(eventId, recipientEmail, subject, body, LocalDateTime.now());
    }

    public void markSent(final LocalDateTime sentAt) {
        this.status = EmailDeliveryStatus.SENT;
        this.sentAt = sentAt;
        this.failedAt = null;
        this.failureReason = null;
    }

    public void markFailed(final LocalDateTime failedAt, final String failureReason) {
        if (status == EmailDeliveryStatus.SENT) {
            return;
        }

        this.status = EmailDeliveryStatus.FAILED;
        this.failedAt = failedAt;
        this.failureReason = truncate(failureReason);
    }

    private String truncate(final String value) {
        if (value == null || value.length() <= MAX_FAILURE_REASON_LENGTH) {
            return value;
        }

        return value.substring(0, MAX_FAILURE_REASON_LENGTH);
    }
}
