CREATE TABLE launch_assessment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    project_name VARCHAR(128) NOT NULL,
    repository_url VARCHAR(512) NULL,
    project_summary TEXT NOT NULL,
    target_audience VARCHAR(128) NULL,
    budget VARCHAR(128) NULL,
    region VARCHAR(64) NULL,
    expected_users VARCHAR(128) NULL,
    upload_types VARCHAR(128) NULL,
    payment_required TINYINT(1) NOT NULL DEFAULT 0,
    current_stage TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_launch_assessment_account (account_id, update_time),
    KEY idx_launch_assessment_status (status, update_time),
    CONSTRAINT fk_launch_assessment_account FOREIGN KEY (account_id) REFERENCES account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目上线体检';

CREATE TABLE launch_assessment_stage (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assessment_id BIGINT NOT NULL,
    stage_no TINYINT NOT NULL,
    stage_title VARCHAR(64) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    segment_cost INT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_launch_assessment_stage (assessment_id, stage_no),
    CONSTRAINT fk_launch_stage_assessment FOREIGN KEY (assessment_id)
        REFERENCES launch_assessment(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上线体检阶段报告';

INSERT IGNORE INTO ai_service_pricing (service_key, service_name, segment_cost)
VALUES ('ai:launch-check', '上线体检（每阶段）', 40);
