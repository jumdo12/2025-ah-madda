package com.ahmadda.infra.notification.mail.outbox.repository;

import com.ahmadda.infra.notification.mail.outbox.EmailOutboxRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailOutboxRecipientRepository extends JpaRepository<EmailOutboxRecipient, Long> {

    List<EmailOutboxRecipient> findAllByEmailOutboxId(final Long emailOutboxId);

    @Query("""
            select r
            from EmailOutboxRecipient r
            where r.emailOutbox.id = :emailOutboxId
              and (
                  r.status = com.ahmadda.infra.notification.mail.outbox.EmailOutboxRecipientStatus.READY
                  or (
                      r.status = com.ahmadda.infra.notification.mail.outbox.EmailOutboxRecipientStatus.RETRY_WAITING
                      and r.nextAttemptAt <= :now
                  )
              )
            order by r.id
            """)
    List<EmailOutboxRecipient> findDispatchableRecipients(
            final Long emailOutboxId,
            final LocalDateTime now
    );
}
