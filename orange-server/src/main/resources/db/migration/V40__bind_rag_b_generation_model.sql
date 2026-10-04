ALTER TABLE rag_b_index_generation
    ADD COLUMN embedding_model VARCHAR(100) NOT NULL DEFAULT 'bge-small-zh-v1.5',
    ADD COLUMN embedding_dimensions INT NOT NULL DEFAULT 512,
    ADD COLUMN chunk_policy VARCHAR(100) NOT NULL DEFAULT 'CHAR_500_V1';

CREATE TABLE rag_b_generation_document (
    generation_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    PRIMARY KEY (generation_id, document_id),
    FOREIGN KEY (generation_id) REFERENCES rag_b_index_generation(id),
    FOREIGN KEY (document_id) REFERENCES rag_b_document(id),
    FOREIGN KEY (version_id) REFERENCES rag_b_document_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
