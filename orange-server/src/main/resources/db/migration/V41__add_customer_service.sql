CREATE TABLE customer_session (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, account_id BIGINT NOT NULL,
 title VARCHAR(120) NOT NULL, state_json LONGTEXT NOT NULL, state_version BIGINT NOT NULL DEFAULT 0,
 summary_text TEXT NULL, summary_through BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 INDEX idx_customer_session_owner(account_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE customer_run (
 id VARCHAR(36) PRIMARY KEY, session_id BIGINT NOT NULL, account_id BIGINT NOT NULL,
 request_id VARCHAR(80) NOT NULL, input_hash CHAR(64) NOT NULL, message TEXT NOT NULL,
 status VARCHAR(24) NOT NULL, stage VARCHAR(40) NOT NULL, result_json LONGTEXT NULL,
 metrics_json LONGTEXT NULL, error_code VARCHAR(60) NULL,
 prompt_version VARCHAR(80) NOT NULL, generation_key VARCHAR(100) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), deadline_at DATETIME(3) NOT NULL,
 finished_at DATETIME(3) NULL,
 UNIQUE KEY uk_customer_request(session_id, request_id),
 INDEX idx_customer_run_active(status, account_id),
 CONSTRAINT fk_customer_run_session FOREIGN KEY(session_id) REFERENCES customer_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE customer_message (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT NOT NULL, run_id VARCHAR(36) NOT NULL,
 role VARCHAR(16) NOT NULL, content LONGTEXT NOT NULL, status VARCHAR(24) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_customer_run_message(run_id, role), INDEX idx_customer_message_history(session_id, id),
 CONSTRAINT fk_customer_message_session FOREIGN KEY(session_id) REFERENCES customer_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE customer_step (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, run_id VARCHAR(36) NOT NULL, tool_name VARCHAR(60) NOT NULL,
 status VARCHAR(24) NOT NULL, detail_json LONGTEXT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), INDEX idx_customer_step_run(run_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE customer_execution_lock (id INT PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO customer_execution_lock(id) VALUES (1);
