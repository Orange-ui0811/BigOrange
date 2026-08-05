ALTER TABLE trend_history
    ADD COLUMN use_persona TINYINT(1) NOT NULL DEFAULT 0 AFTER use_rag;
