CREATE TABLE persona_profile_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    dimension_code VARCHAR(32) NOT NULL,
    content VARCHAR(500) NOT NULL,
    source_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    source_material_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    position_no INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_persona_entry_material (source_material_id),
    KEY idx_persona_entry_account_dimension (account_id, dimension_code, status, position_no),
    CONSTRAINT fk_persona_entry_account FOREIGN KEY (account_id)
        REFERENCES account(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='User-confirmed persona statements grouped by stable workshop dimensions';

CREATE TABLE persona_material (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    dimension_code VARCHAR(32) NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    evidence_ref VARCHAR(128) NULL,
    evidence_summary VARCHAR(500) NOT NULL,
    suggested_content VARCHAR(500) NOT NULL,
    dedupe_key CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    captured_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    reviewed_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_persona_material_dedupe (account_id, dedupe_key),
    KEY idx_persona_material_account_status_time (account_id, status, captured_at),
    CONSTRAINT fk_persona_material_account FOREIGN KEY (account_id)
        REFERENCES account(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Behavior-derived persona evidence awaiting explicit user confirmation';

ALTER TABLE persona_profile_entry
    ADD CONSTRAINT fk_persona_entry_material FOREIGN KEY (source_material_id)
        REFERENCES persona_material(id) ON DELETE SET NULL;

CREATE TABLE persona_equipment (
    account_id BIGINT NOT NULL,
    slot_code VARCHAR(32) NOT NULL,
    item_code VARCHAR(48) NOT NULL,
    variant_code VARCHAR(32) NOT NULL DEFAULT 'CLASSIC',
    color_hex CHAR(7) NOT NULL DEFAULT '#F37021',
    equipped TINYINT(1) NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (account_id, slot_code),
    CONSTRAINT fk_persona_equipment_account FOREIGN KEY (account_id)
        REFERENCES account(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Independent visual equipment state unlocked by confirmed persona dimensions';

CREATE TABLE persona_workshop_setting (
    account_id BIGINT NOT NULL,
    showcase_enabled TINYINT(1) NOT NULL DEFAULT 0,
    avatar_enabled TINYINT(1) NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (account_id),
    CONSTRAINT fk_persona_setting_account FOREIGN KEY (account_id)
        REFERENCES account(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Account-level persona workshop display preferences';
