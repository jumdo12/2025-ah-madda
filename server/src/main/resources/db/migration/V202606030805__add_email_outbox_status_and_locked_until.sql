alter table email_outbox
    add column status varchar(20) not null default 'READY',
    add column locked_until datetime(6) null,
    modify locked_at datetime(6) null;

create index idx_email_outbox_dispatch
    on email_outbox (status, locked_until, email_outbox_id);
