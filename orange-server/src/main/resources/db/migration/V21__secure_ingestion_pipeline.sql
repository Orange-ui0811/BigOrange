CREATE TABLE ingestion_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    pipeline_type VARCHAR(64) NOT NULL,
    trigger_source VARCHAR(32) NOT NULL,
    business_key VARCHAR(160) NOT NULL,
    config_type VARCHAR(32) NULL,
    config_version VARCHAR(64) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    progress INT NOT NULL DEFAULT 0,
    cancel_requested TINYINT(1) NOT NULL DEFAULT 0,
    error_summary VARCHAR(2000) NULL,
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ingestion_run_business (pipeline_type, business_key),
    KEY idx_ingestion_run_status_time (status, create_time),
    KEY idx_ingestion_run_pipeline_time (pipeline_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='External ingestion and translation runs';

CREATE TABLE ingestion_run_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    stage VARCHAR(32) NOT NULL,
    item_key VARCHAR(256) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    result_json JSON NULL,
    last_error VARCHAR(2000) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    complete_time DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ingestion_item (run_id, stage, item_key),
    KEY idx_ingestion_item_status (run_id, status, id),
    CONSTRAINT fk_ingestion_item_run FOREIGN KEY (run_id) REFERENCES ingestion_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Per-item ingestion progress and failures';

CREATE TABLE ingestion_pipeline_lock (
    pipeline_key VARCHAR(96) NOT NULL,
    run_id BIGINT NOT NULL,
    owner_id VARCHAR(96) NOT NULL,
    lease_until DATETIME NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (pipeline_key),
    KEY idx_ingestion_lock_lease (lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Cross-instance ingestion pipeline lease';

CREATE TABLE ai_translation_cache (
    content_hash CHAR(64) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    model VARCHAR(128) NOT NULL,
    translated_json MEDIUMTEXT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    hit_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (content_hash, purpose, model),
    KEY idx_translation_cache_used (last_used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Persistent AI translation cache';

ALTER TABLE system_task
    ADD COLUMN run_id BIGINT NULL AFTER id,
    ADD COLUMN config_version VARCHAR(64) NULL AFTER payload_json,
    ADD COLUMN lease_until DATETIME NULL AFTER locked_at,
    ADD COLUMN cancel_requested TINYINT(1) NOT NULL DEFAULT 0 AFTER lease_until,
    ADD KEY idx_system_task_run (run_id, status),
    ADD KEY idx_system_task_lease (status, lease_until);

ALTER TABLE open_source_issue
    ADD COLUMN visibility_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' AFTER translation_status,
    ADD COLUMN verification_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' AFTER visibility_status,
    ADD COLUMN translation_attempts INT NOT NULL DEFAULT 0 AFTER verification_status,
    ADD COLUMN translation_error VARCHAR(1000) NULL AFTER translation_attempts,
    ADD KEY idx_open_source_visibility (visibility_status, state, github_updated_at);

UPDATE open_source_issue
SET visibility_status = CASE
        WHEN published = 1 THEN 'LEGACY_VISIBLE'
        ELSE 'HIDDEN'
    END,
    verification_status = CASE
        WHEN verified_at IS NOT NULL THEN 'DONE'
        ELSE 'PENDING'
    END;

ALTER TABLE knowledge_document
    ADD COLUMN original_content MEDIUMTEXT NULL AFTER content,
    ADD COLUMN fetch_status VARCHAR(16) NOT NULL DEFAULT 'DONE' AFTER original_content,
    ADD COLUMN translation_status VARCHAR(16) NOT NULL DEFAULT 'DONE' AFTER fetch_status,
    ADD COLUMN storage_status VARCHAR(16) NOT NULL DEFAULT 'DONE' AFTER translation_status,
    ADD COLUMN vector_status VARCHAR(16) NOT NULL DEFAULT 'DONE' AFTER storage_status,
    ADD COLUMN pipeline_error VARCHAR(1000) NULL AFTER vector_status,
    ADD KEY idx_knowledge_pipeline (source, vector_status, create_time);

INSERT INTO leaderboard_snapshot (source, topic_name, period, snapshot_date, data_json, create_time)
SELECT source, '__ALL__', period, snapshot_date, data_json, create_time
FROM leaderboard_snapshot
WHERE topic_name IS NULL
ON DUPLICATE KEY UPDATE
    data_json = VALUES(data_json),
    create_time = VALUES(create_time);
