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
public class EmailDeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_delivery_attempt_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_outbox_id", nullable = false)
    private EmailOutbox emailOutbox;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_outbox_recipient_id", nullable = false)
    private EmailOutboxRecipient emailOutboxRecipient;

    @Column(nullable = false)
    private String recipientEmail;

    @Column(nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailDeliveryAttemptResult result;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime attemptedAt;

    private EmailDeliveryAttempt(
            final EmailOutbox emailOutbox,
            final EmailOutboxRecipient emailOutboxRecipient,
            final String recipientEmail,
            final int attemptNumber,
            final EmailDeliveryAttemptResult result,
            final String errorMessage,
            final LocalDateTime attemptedAt
    ) {
        this.emailOutbox = emailOutbox;
        this.emailOutboxRecipient = emailOutboxRecipient;
        this.recipientEmail = recipientEmail;
        this.attemptNumber = attemptNumber;
        this.result = result;
        this.errorMessage = errorMessage;
        this.attemptedAt = attemptedAt;
    }

    public static EmailDeliveryAttempt create(
            final EmailOutbox emailOutbox,
            final EmailOutboxRecipient emailOutboxRecipient,
            final int attemptNumber,
            final EmailDeliveryAttemptResult result,
            final String errorMessage,
            final LocalDateTime attemptedAt
    ) {
        return new EmailDeliveryAttempt(
                emailOutbox,
                emailOutboxRecipient,
                emailOutboxRecipient.getRecipientEmail(),
                attemptNumber,
                result,
                errorMessage,
                attemptedAt
        );
    }
}
