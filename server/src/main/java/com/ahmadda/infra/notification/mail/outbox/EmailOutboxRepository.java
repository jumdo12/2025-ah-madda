package com.ahmadda.infra.notification.mail.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    Optional<EmailOutbox> findTopByRecipientEmailAndSubjectAndBodyOrderByCreatedAtDesc(
            final String recipientEmail,
            final String subject,
            final String body
    );
}
