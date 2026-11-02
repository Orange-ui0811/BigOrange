CREATE TABLE customer_source_fact (
    id VARCHAR(120) NOT NULL PRIMARY KEY,
    fact_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    revision BIGINT NOT NULL DEFAULT 1,
    reviewed_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_customer_fact_status(status)
);
CREATE TABLE customer_source_fact_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    fact_id VARCHAR(120) NOT NULL,
    revision BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    actor_id BIGINT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_customer_fact_event(fact_id,id)
);
