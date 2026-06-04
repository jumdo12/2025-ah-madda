alter table email_outbox
    add column reference_type varchar(50),
    add column reference_id bigint;

create index idx_email_outbox_reference
    on email_outbox (reference_type, reference_id);
