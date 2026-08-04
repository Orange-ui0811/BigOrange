ALTER TABLE account
    ADD COLUMN password_version BIGINT NOT NULL DEFAULT 0
        COMMENT 'Incremented whenever the password changes';
