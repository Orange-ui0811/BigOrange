ALTER TABLE customer_run
    ADD COLUMN execution_engine VARCHAR(24) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN graph_version VARCHAR(64) NULL,
    ADD COLUMN state_schema_version INT NOT NULL DEFAULT 1,
    ADD COLUMN trace_id VARCHAR(64) NULL;

CREATE TABLE customer_graph_execution (
    run_id VARCHAR(36) PRIMARY KEY,
    thread_id VARCHAR(80) NOT NULL UNIQUE,
    graph_version VARCHAR(64) NOT NULL,
    state_schema_version INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    error_code VARCHAR(80) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3) NULL
);
CREATE TABLE customer_graph_checkpoint (
    sequence_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id VARCHAR(36) NOT NULL,
    checkpoint_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(80) NOT NULL,
    next_node_id VARCHAR(80) NULL,
    state_json MEDIUMTEXT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_customer_checkpoint(run_id,checkpoint_id),
    KEY ix_customer_checkpoint_run(run_id,sequence_id)
);
CREATE TABLE customer_node_execution (
    id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    execution_engine VARCHAR(24) NOT NULL,
    node_id VARCHAR(80) NOT NULL,
    status VARCHAR(24) NOT NULL,
    next_node_id VARCHAR(80) NULL,
    duration_ms BIGINT NULL,
    error_code VARCHAR(80) NULL,
    summary_json MEDIUMTEXT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3) NULL,
    KEY ix_customer_node_run(run_id,created_at)
);
CREATE TABLE customer_raw_payload (
    id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    node_execution_id VARCHAR(36) NULL,
    kind VARCHAR(40) NOT NULL,
    content_json MEDIUMTEXT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY ix_customer_raw_run(run_id,created_at)
);
CREATE TABLE customer_raw_access_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_id BIGINT NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    payload_id VARCHAR(36) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY ix_customer_raw_audit_time(created_at)
);
