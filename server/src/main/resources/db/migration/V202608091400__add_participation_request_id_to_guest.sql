ALTER TABLE guest
    ADD COLUMN participation_request_id BINARY(16) NULL;

UPDATE guest
SET participation_request_id = UUID_TO_BIN(UUID())
WHERE participation_request_id IS NULL;

ALTER TABLE guest
    MODIFY COLUMN participation_request_id BINARY(16) NOT NULL,
    ADD CONSTRAINT UK_guest__participation_request_id
        UNIQUE (participation_request_id);
