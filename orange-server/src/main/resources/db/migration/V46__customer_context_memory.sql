-- Append-only source references; original messages remain the sole archive.
CREATE TABLE customer_context_run (
 run_id VARCHAR(36) PRIMARY KEY,
 session_id BIGINT NOT NULL,
 task_id VARCHAR(100) NOT NULL,
 requirement_version BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 INDEX idx_customer_context_task(session_id,task_id),
 CONSTRAINT fk_customer_context_run FOREIGN KEY(run_id) REFERENCES customer_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE customer_context_summary (
 id VARCHAR(36) PRIMARY KEY,
 session_id BIGINT NOT NULL,
 task_id VARCHAR(100) NOT NULL,
 requirement_version BIGINT NOT NULL,
 from_message_id BIGINT NOT NULL,
 through_message_id BIGINT NOT NULL,
 source_ids_json LONGTEXT NOT NULL,
 source_hash CHAR(64) NOT NULL,
 summary_text TEXT NOT NULL,
 model VARCHAR(120) NOT NULL,
 prompt_version VARCHAR(80) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_customer_context_summary(session_id,task_id,source_hash),
 INDEX idx_customer_context_summary_scope(session_id,task_id,through_message_id),
 CONSTRAINT fk_customer_context_summary_session FOREIGN KEY(session_id) REFERENCES customer_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE customer_context_summary_lease (
 session_id BIGINT NOT NULL,
 task_id VARCHAR(100) NOT NULL,
 lease_token VARCHAR(36) NOT NULL DEFAULT '',
 lease_until DATETIME(3) NULL,
 PRIMARY KEY(session_id,task_id),
 CONSTRAINT fk_customer_context_lease_session FOREIGN KEY(session_id) REFERENCES customer_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
