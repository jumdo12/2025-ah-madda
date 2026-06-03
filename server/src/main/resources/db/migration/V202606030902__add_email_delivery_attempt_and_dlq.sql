alter table email_outbox_recipient
    add column status varchar(20) not null default 'READY',
    add column attempt_count int not null default 0,
    add column next_attempt_at datetime(6) null,
    add column sent_at datetime(6) null,
    add column failed_at datetime(6) null,
    add column last_error_message varchar(1000) null;

create index idx_email_outbox_recipient_dispatch
    on email_outbox_recipient (email_outbox_id, status, next_attempt_at, email_outbox_recipient_id);

create table email_delivery_attempt
(
    email_delivery_attempt_id bigint auto_increment
        primary key,
    email_outbox_id           bigint        not null,
    email_outbox_recipient_id bigint        not null,
    recipient_email           varchar(255)  not null,
    attempt_number            int           not null,
    result                    varchar(30)   not null,
    error_message             varchar(1000) null,
    attempted_at              datetime(6)   not null,
    constraint fk_email_delivery_attempt__email_outbox
        foreign key (email_outbox_id)
            references email_outbox (email_outbox_id),
    constraint fk_email_delivery_attempt__email_outbox_recipient
        foreign key (email_outbox_recipient_id)
            references email_outbox_recipient (email_outbox_recipient_id)
);

create index idx_email_delivery_attempt_recipient
    on email_delivery_attempt (email_outbox_recipient_id, attempt_number);

create table email_dead_letter
(
    email_dead_letter_id      bigint auto_increment
        primary key,
    email_outbox_id           bigint        not null,
    email_outbox_recipient_id bigint        not null,
    recipient_email           varchar(255)  not null,
    reason                    varchar(30)   not null,
    error_message             varchar(1000) null,
    attempt_count             int           not null,
    failed_at                 datetime(6)   not null,
    constraint uk_email_dead_letter_recipient
        unique (email_outbox_recipient_id),
    constraint fk_email_dead_letter__email_outbox
        foreign key (email_outbox_id)
            references email_outbox (email_outbox_id),
    constraint fk_email_dead_letter__email_outbox_recipient
        foreign key (email_outbox_recipient_id)
            references email_outbox_recipient (email_outbox_recipient_id)
);

create index idx_email_dead_letter_outbox
    on email_dead_letter (email_outbox_id, failed_at);
