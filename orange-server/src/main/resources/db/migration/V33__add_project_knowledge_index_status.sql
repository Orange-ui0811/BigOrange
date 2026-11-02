CREATE TABLE IF NOT EXISTS project_knowledge_index_status (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_type VARCHAR(32) NOT NULL COMMENT 'BEGINNER_PROJECT / CAREER_PROJECT',
    project_id BIGINT NOT NULL,
    source_ref VARCHAR(255) NOT NULL,
    knowledge_document_id BIGINT NULL,
    content_hash VARCHAR(64) NULL,
    index_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / INDEXING / DONE / FAILED / REMOVED',
    chunk_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    last_indexed_at DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_knowledge_type_id (project_type, project_id),
    UNIQUE KEY uk_project_knowledge_source_ref (source_ref),
    KEY idx_project_knowledge_status (index_status),
    KEY idx_project_knowledge_document (knowledge_document_id),
    CONSTRAINT fk_project_knowledge_document
        FOREIGN KEY (knowledge_document_id) REFERENCES knowledge_document(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO project_knowledge_index_status
    (project_type, project_id, source_ref, content_hash, index_status)
SELECT 'BEGINNER_PROJECT', id, CONCAT('beginner:', id), content_hash, 'PENDING'
FROM beginner_project
WHERE status = 'PUBLISHED'
ON DUPLICATE KEY UPDATE content_hash = VALUES(content_hash), index_status = 'PENDING';

INSERT INTO project_knowledge_index_status
    (project_type, project_id, source_ref, content_hash, index_status)
SELECT 'CAREER_PROJECT', project_id, CONCAT('career:', project_id), content_hash, 'PENDING'
FROM career_project_profile
WHERE status = 'PUBLISHED'
ON DUPLICATE KEY UPDATE content_hash = VALUES(content_hash), index_status = 'PENDING';
