-- Historical generations retain their original provider; never silently query cloud vectors with a local model.
ALTER TABLE rag_b_index_generation
    ADD COLUMN embedding_provider VARCHAR(24) NOT NULL DEFAULT 'GITEE',
    ADD COLUMN embedding_revision VARCHAR(80) NULL;
