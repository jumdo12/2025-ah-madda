create table event_application_form
(
    application_form_id    bigint auto_increment
        primary key,
    created_at             datetime(6) null,
    updated_at             datetime(6) null,
    deleted_at             datetime(6) null,
    event_id               bigint      not null,
    active_revision_number int         not null,
    constraint UK_event_application_form__event_id
        unique (event_id),
    constraint FK_event_application_form__event__event_id
        foreign key (event_id) references event (event_id)
);

create table application_form_revision
(
    form_revision_id   bigint auto_increment
        primary key,
    created_at         datetime(6) null,
    updated_at         datetime(6) null,
    deleted_at         datetime(6) null,
    application_form_id bigint     not null,
    revision_number    int         not null,
    constraint uk_application_form_revision__form_revision_number
        unique (application_form_id, revision_number),
    constraint FK_application_form_revision__form__application_form_id
        foreign key (application_form_id) references event_application_form (application_form_id)
);

insert into event_application_form (
    created_at,
    updated_at,
    event_id,
    active_revision_number
)
select current_timestamp(6),
       current_timestamp(6),
       event_id,
       1
from event;

insert into application_form_revision (
    created_at,
    updated_at,
    application_form_id,
    revision_number
)
select current_timestamp(6),
       current_timestamp(6),
       application_form_id,
       1
from event_application_form;

alter table question
    add column form_revision_id bigint null after event_id;

update question q
    join event_application_form form
        on form.event_id = q.event_id
    join application_form_revision revision
        on revision.application_form_id = form.application_form_id
        and revision.revision_number = 1
set q.form_revision_id = revision.form_revision_id;

alter table guest
    add column form_revision_id bigint null after event_id;

update guest g
    join event_application_form form
        on form.event_id = g.event_id
    join application_form_revision revision
        on revision.application_form_id = form.application_form_id
        and revision.revision_number = 1
set g.form_revision_id = revision.form_revision_id;

alter table question
    modify column form_revision_id bigint not null,
    add constraint FK_question__form_revision__form_revision_id
        foreign key (form_revision_id) references application_form_revision (form_revision_id),
    drop foreign key FK_question__event__event_id,
    drop column event_id;

alter table guest
    modify column form_revision_id bigint not null,
    add constraint FK_guest__form_revision__form_revision_id
        foreign key (form_revision_id) references application_form_revision (form_revision_id);
