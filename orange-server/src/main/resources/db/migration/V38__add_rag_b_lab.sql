CREATE TABLE IF NOT EXISTS rag_b_index_generation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    generation_key VARCHAR(64) NOT NULL,
    redis_prefix VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'BUILDING' COMMENT 'BUILDING / READY / ACTIVE / FAILED / RETIRED',
    active TINYINT(1) NOT NULL DEFAULT 0,
    document_count INT NOT NULL DEFAULT 0,
    chunk_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(2000) NULL,
    activated_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_rag_b_generation_key (generation_key),
    KEY idx_rag_b_generation_active (active, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='B 版 RAG 的隔离索引代际';

INSERT INTO rag_b_index_generation
    (generation_key, redis_prefix, status, active, activated_at)
VALUES
    ('bootstrap', 'orange:rag:b:g:bootstrap:', 'ACTIVE', 1, NOW(3));

CREATE TABLE IF NOT EXISTS rag_b_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_type VARCHAR(32) NOT NULL COMMENT 'MANUAL / GITHUB / BEGINNER_PROJECT / CAREER_PROJECT / JOB_POSTING',
    source_ref VARCHAR(512) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PROCESSING / READY / FAILED / DELETED',
    active_version_id BIGINT NULL,
    content_hash CHAR(64) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(2000) NULL,
    deleted_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_rag_b_document_source (source_type, source_ref),
    KEY idx_rag_b_document_status (status),
    KEY idx_rag_b_document_active_version (active_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='B 版 RAG 文档身份与发布状态';

CREATE TABLE IF NOT EXISTS rag_b_document_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    original_content LONGTEXT NOT NULL,
    translated_content LONGTEXT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PROCESSING / READY / FAILED / SUPERSEDED',
    chunk_count INT NOT NULL DEFAULT 0,
    indexed_generation_id BIGINT NULL,
    last_error VARCHAR(2000) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_rag_b_document_version (document_id, version_no),
    KEY idx_rag_b_version_status (status),
    KEY idx_rag_b_version_generation (indexed_generation_id),
    CONSTRAINT fk_rag_b_version_document FOREIGN KEY (document_id) REFERENCES rag_b_document(id),
    CONSTRAINT fk_rag_b_version_generation FOREIGN KEY (indexed_generation_id) REFERENCES rag_b_index_generation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='B 版 RAG 不可变文档版本';

CREATE TABLE IF NOT EXISTS rag_b_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    section_path VARCHAR(1000) NULL,
    start_offset INT NOT NULL,
    end_offset INT NOT NULL,
    content TEXT NOT NULL,
    metadata_json JSON NULL,
    vector_key VARCHAR(255) NULL,
    index_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / INDEXED / FAILED / DELETED',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_rag_b_chunk_version_index (version_id, chunk_index),
    KEY idx_rag_b_chunk_document (document_id),
    KEY idx_rag_b_chunk_status (index_status),
    CONSTRAINT fk_rag_b_chunk_document FOREIGN KEY (document_id) REFERENCES rag_b_document(id),
    CONSTRAINT fk_rag_b_chunk_version FOREIGN KEY (version_id) REFERENCES rag_b_document_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='B 版 RAG 可追溯分块';

CREATE TABLE IF NOT EXISTS rag_b_experiment_run (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_key VARCHAR(96) NOT NULL,
    mode VARCHAR(24) NOT NULL COMMENT 'A_BASELINE / B_BASE / B_ENHANCED',
    dataset_version VARCHAR(96) NOT NULL,
    model_snapshot JSON NULL,
    retrieval_snapshot JSON NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    result_json JSON NULL,
    last_error VARCHAR(2000) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_rag_b_experiment_run_key (run_key),
    KEY idx_rag_b_experiment_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='A/B 对比实验记录';
