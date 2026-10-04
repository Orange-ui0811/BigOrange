ALTER TABLE rag_b_index_generation
    ADD COLUMN adapter_type VARCHAR(24) NOT NULL DEFAULT 'EXACT'
        COMMENT 'EXACT / HNSW_HYBRID' AFTER redis_prefix;

UPDATE rag_b_index_generation SET adapter_type = 'EXACT' WHERE adapter_type IS NULL OR adapter_type = '';

CREATE TABLE IF NOT EXISTS rag_b_generation_chunk (
    generation_id BIGINT NOT NULL,
    chunk_id BIGINT NOT NULL,
    vector_key VARCHAR(255) NOT NULL,
    index_status VARCHAR(24) NOT NULL DEFAULT 'INDEXED',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (generation_id, chunk_id),
    UNIQUE KEY uk_rag_b_generation_vector_key (vector_key),
    KEY idx_rag_b_generation_chunk (chunk_id),
    CONSTRAINT fk_rag_b_generation_chunk_generation FOREIGN KEY (generation_id) REFERENCES rag_b_index_generation(id),
    CONSTRAINT fk_rag_b_generation_chunk_chunk FOREIGN KEY (chunk_id) REFERENCES rag_b_chunk(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='B 版索引代际与向量键映射';
