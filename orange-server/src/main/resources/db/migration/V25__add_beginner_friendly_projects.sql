CREATE TABLE beginner_project (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(180) NOT NULL,
    owner_name VARCHAR(100) NOT NULL,
    repository_name VARCHAR(100) NOT NULL,
    html_url VARCHAR(512) NOT NULL,
    description_original VARCHAR(2000) NULL,
    description_zh VARCHAR(2000) NULL,
    beginner_reason VARCHAR(3000) NULL,
    modification_ideas VARCHAR(3000) NULL,
    primary_language VARCHAR(64) NULL,
    topics_json JSON NULL,
    tech_stack VARCHAR(512) NULL,
    package_manager VARCHAR(32) NULL,
    node_version VARCHAR(64) NULL,
    stars INT NOT NULL DEFAULT 0,
    forks INT NOT NULL DEFAULT 0,
    open_issues INT NOT NULL DEFAULT 0,
    archived TINYINT(1) NOT NULL DEFAULT 0,
    has_readme TINYINT(1) NOT NULL DEFAULT 0,
    requires_docker TINYINT(1) NOT NULL DEFAULT 0,
    requires_database TINYINT(1) NOT NULL DEFAULT 0,
    requires_secrets TINYINT(1) NOT NULL DEFAULT 0,
    requires_external_service TINYINT(1) NOT NULL DEFAULT 0,
    difficulty VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    estimated_duration VARCHAR(64) NULL,
    beginner_score INT NOT NULL DEFAULT 0,
    score_version VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CANDIDATE',
    hidden_reason VARCHAR(500) NULL,
    content_hash CHAR(64) NULL,
    github_created_at DATETIME NULL,
    github_updated_at DATETIME NULL,
    last_verified_at DATETIME NULL,
    source_run_id BIGINT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_beginner_project_full_name (full_name),
    KEY idx_beginner_project_public (status, beginner_score, github_updated_at),
    KEY idx_beginner_project_language (primary_language, status),
    KEY idx_beginner_project_verified (status, last_verified_at),
    KEY idx_beginner_project_run (source_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Verified beginner-friendly GitHub repositories';

CREATE TABLE beginner_project_score_evidence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    run_id BIGINT NULL,
    score_version VARCHAR(32) NOT NULL,
    dimension_key VARCHAR(32) NOT NULL,
    score INT NOT NULL,
    max_score INT NOT NULL,
    evidence_text VARCHAR(500) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_beginner_evidence (project_id, score_version, dimension_key),
    KEY idx_beginner_evidence_run (run_id),
    CONSTRAINT fk_beginner_evidence_project FOREIGN KEY (project_id)
        REFERENCES beginner_project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Deterministic beginner score evidence';

CREATE TABLE beginner_project_weekly_pick (
    id BIGINT NOT NULL AUTO_INCREMENT,
    week_start DATE NOT NULL,
    project_id BIGINT NOT NULL,
    rank_no INT NOT NULL,
    selection_source VARCHAR(16) NOT NULL DEFAULT 'ALGORITHM',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_beginner_week_project (week_start, project_id),
    UNIQUE KEY uk_beginner_week_rank (week_start, rank_no),
    CONSTRAINT fk_beginner_pick_project FOREIGN KEY (project_id)
        REFERENCES beginner_project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Weekly beginner-friendly project selections';

ALTER TABLE trend_history
    ADD COLUMN prefer_beginner_projects TINYINT(1) NOT NULL DEFAULT 0 AFTER use_rag;
