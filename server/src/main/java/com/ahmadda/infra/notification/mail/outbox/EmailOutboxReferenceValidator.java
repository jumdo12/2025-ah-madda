package com.ahmadda.infra.notification.mail.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailOutboxReferenceValidator {

    private final JdbcTemplate jdbcTemplate;

    public boolean canDispatch(final EmailOutbox outbox) {
        if (!outbox.isEventReference()) {
            return true;
        }

        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from event
                        where event_id = ?
                          and deleted_at is null
                        """,
                Integer.class,
                outbox.getReferenceId()
        );

        return count != null && count > 0;
    }
}
