alter table email_outbox_recipient
    add column event_id bigint null,
    add column subject varchar(255) null,
    add column body longtext null,
    add column status varchar(20) not null default 'PENDING',
    add column created_at datetime(6) null,
    add column sent_at datetime(6) null,
    add column failed_at datetime(6) null,
    add column failure_reason varchar(1000) null;

update email_outbox_recipient recipient
    join email_outbox outbox
        on outbox.email_outbox_id = recipient.email_outbox_id
set recipient.subject = outbox.subject,
    recipient.body = outbox.body,
    recipient.created_at = outbox.created_at;

alter table email_outbox_recipient
    drop foreign key fk_email_outbox_recipient__email_outbox,
    drop column email_outbox_id;

drop table email_outbox;

rename table email_outbox_recipient to email_outbox;

alter table email_outbox
    change column email_outbox_recipient_id email_outbox_id bigint not null auto_increment,
    modify column subject varchar(255) not null,
    modify column body longtext not null,
    modify column created_at datetime(6) not null,
    add constraint fk_email_outbox__event
        foreign key (event_id)
            references event (event_id);
