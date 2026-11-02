CREATE TABLE persona_appearance_share (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(16) NOT NULL,
    owner_account_id BIGINT NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    payload_json JSON NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    import_count INT NOT NULL DEFAULT 0,
    last_imported_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_persona_appearance_share_code (code),
    KEY idx_persona_appearance_share_owner_status (owner_account_id, status, create_time),
    CONSTRAINT fk_persona_appearance_share_owner FOREIGN KEY (owner_account_id)
        REFERENCES account(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Versioned visual-only persona appearance share codes';
