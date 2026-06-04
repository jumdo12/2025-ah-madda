package com.ahmadda.infra.notification.mail.outbox.repository;

import com.ahmadda.infra.notification.mail.outbox.EmailDeadLetter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EmailDeadLetterRepository extends JpaRepository<EmailDeadLetter, Long> {

    @EntityGraph(attributePaths = {"emailOutbox", "emailOutboxRecipient"})
    Page<EmailDeadLetter> findAllByOrderByFailedAtDescIdDesc(final Pageable pageable);

    @Query("""
            select d
            from EmailDeadLetter d
            join fetch d.emailOutbox
            join fetch d.emailOutboxRecipient
            where d.id = :id
            """)
    Optional<EmailDeadLetter> findWithAssociationsById(final Long id);
}
