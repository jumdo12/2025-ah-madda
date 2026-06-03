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
public class EmailDeadLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_dead_letter_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_outbox_id", nullable = false)
    private EmailOutbox emailOutbox;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_outbox_recipient_id", nullable = false, unique = true)
    private EmailOutboxRecipient emailOutboxRecipient;

    @Column(nullable = false)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailDeadLetterReason reason;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private LocalDateTime failedAt;

    private EmailDeadLetter(
            final EmailOutbox emailOutbox,
            final EmailOutboxRecipient emailOutboxRecipient,
            final String recipientEmail,
            final EmailDeadLetterReason reason,
            final String errorMessage,
            final int attemptCount,
            final LocalDateTime failedAt
    ) {
        this.emailOutbox = emailOutbox;
        this.emailOutboxRecipient = emailOutboxRecipient;
        this.recipientEmail = recipientEmail;
        this.reason = reason;
        this.errorMessage = errorMessage;
        this.attemptCount = attemptCount;
        this.failedAt = failedAt;
    }

    public static EmailDeadLetter create(
            final EmailOutbox emailOutbox,
            final EmailOutboxRecipient emailOutboxRecipient,
            final EmailDeadLetterReason reason,
            final String errorMessage,
            final LocalDateTime failedAt
    ) {
        return new EmailDeadLetter(
                emailOutbox,
                emailOutboxRecipient,
                emailOutboxRecipient.getRecipientEmail(),
                reason,
                errorMessage,
                emailOutboxRecipient.getAttemptCount(),
                failedAt
        );
    }
}
