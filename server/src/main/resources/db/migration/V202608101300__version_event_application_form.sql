CREATE TABLE event_application_form
(
    application_form_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at            DATETIME(6) NULL,
    updated_at            DATETIME(6) NULL,
    deleted_at            DATETIME(6) NULL,
    event_id              BIGINT      NOT NULL,
    active_version_number INT         NOT NULL,
    lock_version          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT UK_event_application_form__event_id
        UNIQUE (event_id),
    CONSTRAINT FK_event_application_form__event__event_id
        FOREIGN KEY (event_id) REFERENCES event (event_id)
);

CREATE TABLE application_form_version
(
    form_version_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at          DATETIME(6) NULL,
    updated_at          DATETIME(6) NULL,
    deleted_at          DATETIME(6) NULL,
    application_form_id BIGINT      NOT NULL,
    version_number      INT         NOT NULL,
    CONSTRAINT UK_application_form_version__form_version_number
        UNIQUE (application_form_id, version_number),
    CONSTRAINT FK_application_form_version__form__application_form_id
        FOREIGN KEY (application_form_id) REFERENCES event_application_form (application_form_id)
);

INSERT INTO event_application_form (
    created_at,
    updated_at,
    event_id,
    active_version_number,
    lock_version
)
SELECT CURRENT_TIMESTAMP(6),
       CURRENT_TIMESTAMP(6),
       event_id,
       1,
       0
FROM event;

INSERT INTO application_form_version (
    created_at,
    updated_at,
    application_form_id,
    version_number
)
SELECT CURRENT_TIMESTAMP(6),
       CURRENT_TIMESTAMP(6),
       application_form_id,
       1
FROM event_application_form;

ALTER TABLE question
    ADD COLUMN form_version_id BIGINT NULL AFTER event_id;

UPDATE question q
    JOIN event_application_form form
        ON form.event_id = q.event_id
    JOIN application_form_version version
        ON version.application_form_id = form.application_form_id
        AND version.version_number = 1
SET q.form_version_id = version.form_version_id;

ALTER TABLE guest
    ADD COLUMN form_version_id BIGINT NULL AFTER event_id;

UPDATE guest g
    JOIN event_application_form form
        ON form.event_id = g.event_id
    JOIN application_form_version version
        ON version.application_form_id = form.application_form_id
        AND version.version_number = 1
SET g.form_version_id = version.form_version_id;

ALTER TABLE question
    MODIFY COLUMN form_version_id BIGINT NOT NULL,
    ADD CONSTRAINT FK_question__form_version__form_version_id
        FOREIGN KEY (form_version_id) REFERENCES application_form_version (form_version_id),
    DROP FOREIGN KEY FK_question__event__event_id,
    DROP COLUMN event_id;

ALTER TABLE guest
    MODIFY COLUMN form_version_id BIGINT NOT NULL,
    ADD CONSTRAINT FK_guest__form_version__form_version_id
        FOREIGN KEY (form_version_id) REFERENCES application_form_version (form_version_id);
