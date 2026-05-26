ALTER TABLE guest
    ADD CONSTRAINT UK_guest__event_id__participant_id
        UNIQUE (event_id, participant_id);
