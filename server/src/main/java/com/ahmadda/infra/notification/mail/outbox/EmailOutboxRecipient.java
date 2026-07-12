package com.ahmadda.infra.notification.mail.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailOutboxRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_outbox_recipient_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_outbox_id", nullable = false)
    private EmailOutbox emailOutbox;

    @Column(nullable = false)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailOutboxRecipientStatus status;

    @Column(nullable = false)
    private int attemptCount;

    private LocalDateTime nextAttemptAt;

    private LocalDateTime sentAt;

    private LocalDateTime failedAt;

    @Column(length = 1000)
    private String lastErrorMessage;

    private EmailOutboxRecipient(
            final EmailOutbox emailOutbox,
            final String recipientEmail,
            final EmailOutboxRecipientStatus status,
            final int attemptCount,
            final LocalDateTime nextAttemptAt,
            final LocalDateTime sentAt,
            final LocalDateTime failedAt,
            final String lastErrorMessage
    ) {
        this.emailOutbox = emailOutbox;
        this.recipientEmail = recipientEmail;
        this.status = status;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.sentAt = sentAt;
        this.failedAt = failedAt;
        this.lastErrorMessage = lastErrorMessage;
    }

    public static EmailOutboxRecipient create(final EmailOutbox emailOutbox, final String recipientEmail) {
        return new EmailOutboxRecipient(
                emailOutbox,
                recipientEmail,
                EmailOutboxRecipientStatus.READY,
                0,
                null,
                null,
                null,
                null
        );
    }

    public int nextAttemptNumber() {
        return attemptCount + 1;
    }

    public void markProcessing() {
        this.status = EmailOutboxRecipientStatus.PROCESSING;
    }

    public void markSent(final LocalDateTime sentAt, final int attemptNumber) {
        this.status = EmailOutboxRecipientStatus.SENT;
        this.attemptCount = attemptNumber;
        this.nextAttemptAt = null;
        this.sentAt = sentAt;
        this.failedAt = null;
        this.lastErrorMessage = null;
    }

    public void scheduleRetry(
            final LocalDateTime nextAttemptAt,
            final String errorMessage,
            final int attemptNumber
    ) {
        this.status = EmailOutboxRecipientStatus.RETRY_WAITING;
        this.attemptCount = attemptNumber;
        this.nextAttemptAt = nextAttemptAt;
        this.sentAt = null;
        this.failedAt = null;
        this.lastErrorMessage = errorMessage;
    }

    public void markRateLimitWaiting(final LocalDateTime nextAttemptAt, final String reason) {
        this.status = EmailOutboxRecipientStatus.RATE_LIMIT_WAITING;
        this.nextAttemptAt = nextAttemptAt;
        this.sentAt = null;
        this.failedAt = null;
        this.lastErrorMessage = reason;
    }

    public void markFailed(
            final LocalDateTime failedAt,
            final String errorMessage,
            final int attemptNumber
    ) {
        this.status = EmailOutboxRecipientStatus.FAILED;
        this.attemptCount = attemptNumber;
        this.nextAttemptAt = null;
        this.sentAt = null;
        this.failedAt = failedAt;
        this.lastErrorMessage = errorMessage;
    }

    public void markCancelled(final String errorMessage, final int attemptNumber) {
        this.status = EmailOutboxRecipientStatus.CANCELLED;
        this.attemptCount = attemptNumber;
        this.nextAttemptAt = null;
        this.sentAt = null;
        this.failedAt = null;
        this.lastErrorMessage = errorMessage;
    }

    public boolean isPending() {
        return status == EmailOutboxRecipientStatus.READY
                || status == EmailOutboxRecipientStatus.PROCESSING
                || status == EmailOutboxRecipientStatus.RETRY_WAITING
                || status == EmailOutboxRecipientStatus.RATE_LIMIT_WAITING;
    }

    public boolean isSent() {
        return status == EmailOutboxRecipientStatus.SENT;
    }

    public boolean isFailed() {
        return status == EmailOutboxRecipientStatus.FAILED;
    }

    public boolean isCancelled() {
        return status == EmailOutboxRecipientStatus.CANCELLED;
    }
}
