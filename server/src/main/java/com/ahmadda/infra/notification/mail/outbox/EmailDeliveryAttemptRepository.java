package com.ahmadda.infra.notification.mail.outbox;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailDeliveryAttemptRepository extends JpaRepository<EmailDeliveryAttempt, Long> {

    @EntityGraph(attributePaths = {"emailOutbox", "emailOutboxRecipient"})
    List<EmailDeliveryAttempt> findAllByEmailOutboxIdOrderByAttemptedAtDescIdDesc(final Long emailOutboxId);

    @EntityGraph(attributePaths = {"emailOutbox", "emailOutboxRecipient"})
    List<EmailDeliveryAttempt> findAllByEmailOutboxRecipientIdOrderByAttemptedAtDescIdDesc(
            final Long emailOutboxRecipientId
    );
}
