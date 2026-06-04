package com.ahmadda.infra.notification.mail.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    /**
     * 발송 가능한 Outbox 레코드를 조회하고 잠근다.
     * FOR UPDATE SKIP LOCKED로 병렬 처리 시 중복을 방지하며,
     * ORDER BY id LIMIT 50으로 넥스트키 락을 피하고 트랜잭션 락 범위를 제한한다.
     *
     * @param now 잠금 만료 기준 시각
     * @return 발송 가능한 Outbox 레코드 목록 (최대 50건)
     */
    @Query(value = """
            select *
            from email_outbox o
            where (
                  o.status = 'READY'
                  or (o.status = 'PROCESSING' and o.locked_until < :now)
              )
              and (
                  not exists (
                      select 1
                      from email_outbox_recipient r
                      where r.email_outbox_id = o.email_outbox_id
                  )
                  or exists (
                      select 1
                      from email_outbox_recipient r
                      where r.email_outbox_id = o.email_outbox_id
                        and (
                            r.status = 'READY'
                            or (r.status = 'RETRY_WAITING' and r.next_attempt_at <= :now)
                        )
                  )
              )
            order by o.email_outbox_id
            limit 50
            for update skip locked
            """, nativeQuery = true)
    List<EmailOutbox> findAndLockDispatchableOutboxes(final LocalDateTime now);

    @Query(value = """
            select *
            from email_outbox o
            where o.email_outbox_id = :emailOutboxId
              and (
                  o.status = 'READY'
                  or (o.status = 'PROCESSING' and o.locked_until < :now)
              )
              and (
                  not exists (
                      select 1
                      from email_outbox_recipient r
                      where r.email_outbox_id = o.email_outbox_id
                  )
                  or exists (
                      select 1
                      from email_outbox_recipient r
                      where r.email_outbox_id = o.email_outbox_id
                        and (
                            r.status = 'READY'
                            or (r.status = 'RETRY_WAITING' and r.next_attempt_at <= :now)
                        )
                  )
              )
            for update skip locked
            """, nativeQuery = true)
    Optional<EmailOutbox> findAndLockDispatchableOutboxById(
            final Long emailOutboxId,
            final LocalDateTime now
    );
}
