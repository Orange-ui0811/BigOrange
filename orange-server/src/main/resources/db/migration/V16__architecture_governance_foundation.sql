CREATE TABLE IF NOT EXISTS system_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_type VARCHAR(64) NOT NULL,
    business_key VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    payload_json JSON NULL,
    result_json JSON NULL,
    progress INT NOT NULL DEFAULT 0,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    next_run_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_by VARCHAR(96) NULL,
    locked_at DATETIME NULL,
    last_error VARCHAR(2000) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    complete_time DATETIME NULL,
    UNIQUE KEY uk_system_task_business (task_type, business_key),
    KEY idx_system_task_dispatch (status, next_run_at, id),
    KEY idx_system_task_lock (locked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Persistent background task queue';

ALTER TABLE job_posting
    ADD COLUMN audit_source VARCHAR(20) NULL COMMENT 'ADMIN or AI' AFTER reject_reason,
    ADD COLUMN audit_confidence DECIMAL(5,4) NULL AFTER audit_source,
    ADD COLUMN audit_reason VARCHAR(1000) NULL AFTER audit_confidence,
    ADD COLUMN reviewer_id BIGINT NULL AFTER audit_reason,
    ADD COLUMN reviewed_at DATETIME NULL AFTER reviewer_id,
    ADD KEY idx_job_audit_status_time (audit_status, create_time);
