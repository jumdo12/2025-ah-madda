package com.ahmadda.infra.notification.mail.outbox;

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
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_outbox_id")
    private Long id;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailOutboxStatus status;

    @Enumerated(EnumType.STRING)
    private EmailOutboxReferenceType referenceType;

    private Long referenceId;

    private LocalDateTime lockedAt;

    private LocalDateTime lockedUntil;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private EmailOutbox(
            final String subject,
            final String body,
            final EmailOutboxStatus status,
            final EmailOutboxReferenceType referenceType,
            final Long referenceId,
            final LocalDateTime lockedAt,
            final LocalDateTime lockedUntil,
            final LocalDateTime createdAt
    ) {
        this.subject = subject;
        this.body = body;
        this.status = status;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.lockedAt = lockedAt;
        this.lockedUntil = lockedUntil;
        this.createdAt = createdAt;
    }

    public static EmailOutbox createReady(
            final String subject,
            final String body,
            final LocalDateTime createdAt
    ) {
        return new EmailOutbox(subject, body, EmailOutboxStatus.READY, null, null, null, null, createdAt);
    }

    public static EmailOutbox createReadyEvent(
            final String subject,
            final String body,
            final Long eventId,
            final LocalDateTime createdAt
    ) {
        return new EmailOutbox(
                subject,
                body,
                EmailOutboxStatus.READY,
                EmailOutboxReferenceType.EVENT,
                Objects.requireNonNull(eventId),
                null,
                null,
                createdAt
        );
    }

    public static EmailOutbox createProcessing(
            final String subject,
            final String body,
            final LocalDateTime lockedAt,
            final LocalDateTime lockedUntil,
            final LocalDateTime createdAt
    ) {
        return new EmailOutbox(
                subject,
                body,
                EmailOutboxStatus.PROCESSING,
                null,
                null,
                lockedAt,
                lockedUntil,
                createdAt
        );
    }

    public static EmailOutbox createReadyNow(final String subject, final String body) {
        LocalDateTime now = LocalDateTime.now();

        return createReady(subject, body, now);
    }

    public static EmailOutbox createReadyEventNow(final String subject, final String body, final Long eventId) {
        LocalDateTime now = LocalDateTime.now();

        return createReadyEvent(subject, body, eventId, now);
    }

    public boolean isEventReference() {
        return referenceType == EmailOutboxReferenceType.EVENT;
    }

    public void processUntil(final LocalDateTime lockedUntil) {
        this.status = EmailOutboxStatus.PROCESSING;
        this.lockedAt = LocalDateTime.now();
        this.lockedUntil = lockedUntil;
    }

    public void markSent() {
        this.status = EmailOutboxStatus.SENT;
        this.lockedUntil = null;
    }

    public void releaseForRetry() {
        this.status = EmailOutboxStatus.READY;
        this.lockedUntil = null;
    }

    public void markPartiallyCancelled() {
        this.status = EmailOutboxStatus.PARTIAL_CANCELLED;
        this.lockedUntil = null;
    }

    public void markPartiallyFailed() {
        this.status = EmailOutboxStatus.PARTIAL_FAILED;
        this.lockedUntil = null;
    }

    public void markFailed() {
        this.status = EmailOutboxStatus.FAILED;
        this.lockedUntil = null;
    }

    public void markCancelled() {
        this.status = EmailOutboxStatus.CANCELLED;
        this.lockedUntil = null;
    }
}
