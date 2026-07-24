alter table event
    add column registration_closing_reminder_minutes_before int not null default 30;

create index idx_event_registration_end
    on event (registration_end);
