CREATE TABLE career_project_profile (
    project_id BIGINT NOT NULL,
    overall_score INT NOT NULL DEFAULT 0,
    best_role_code VARCHAR(40) NULL,
    difficulty VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    summary_zh TEXT NULL,
    career_value TEXT NULL,
    proof_points TEXT NULL,
    interview_topics TEXT NULL,
    modification_plan TEXT NULL,
    main_risks TEXT NULL,
    estimated_duration VARCHAR(80) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CANDIDATE',
    hidden_reason VARCHAR(500) NULL,
    score_version VARCHAR(32) NOT NULL,
    taxonomy_version VARCHAR(32) NOT NULL,
    content_hash CHAR(64) NULL,
    last_verified_at DATETIME NULL,
    source_run_id BIGINT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id),
    KEY idx_career_project_public (status, overall_score, last_verified_at),
    KEY idx_career_project_role (best_role_code, status, overall_score),
    KEY idx_career_project_run (source_run_id),
    CONSTRAINT fk_career_profile_project FOREIGN KEY (project_id)
        REFERENCES beginner_project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Career growth assessment for shared GitHub repositories';

CREATE TABLE career_project_role_score (
    project_id BIGINT NOT NULL,
    role_code VARCHAR(40) NOT NULL,
    role_relevance INT NOT NULL,
    provable_ability INT NOT NULL,
    knowledge_coverage INT NOT NULL,
    portfolio_completeness INT NOT NULL,
    interview_value INT NOT NULL,
    total_score INT NOT NULL,
    job_sample_count INT NOT NULL DEFAULT 0,
    evidence_json JSON NOT NULL,
    score_version VARCHAR(32) NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, role_code),
    KEY idx_career_role_rank (role_code, total_score, project_id),
    CONSTRAINT fk_career_role_project FOREIGN KEY (project_id)
        REFERENCES beginner_project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Versioned career score by normalized role category';

CREATE TABLE career_project_weekly_pick (
    id BIGINT NOT NULL AUTO_INCREMENT,
    week_start DATE NOT NULL,
    project_id BIGINT NOT NULL,
    rank_no INT NOT NULL,
    selection_source VARCHAR(16) NOT NULL DEFAULT 'ALGORITHM',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_career_week_project (week_start, project_id),
    UNIQUE KEY uk_career_week_rank (week_start, rank_no),
    CONSTRAINT fk_career_pick_project FOREIGN KEY (project_id)
        REFERENCES beginner_project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Weekly career-growth project selections';

ALTER TABLE trend_history
    ADD COLUMN prefer_career_projects TINYINT(1) NOT NULL DEFAULT 0 AFTER prefer_beginner_projects;
