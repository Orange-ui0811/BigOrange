CREATE TABLE IF NOT EXISTS ai_result_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(128) NOT NULL,
    account_id BIGINT NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    business_id VARCHAR(128) NULL,
    purpose VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(500) NULL,
    result_json MEDIUMTEXT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_result_request (request_id),
    KEY idx_ai_result_account_time (account_id, create_time),
    KEY idx_ai_result_account_type_time (account_id, business_type, create_time),
    CONSTRAINT fk_ai_result_account FOREIGN KEY (account_id)
        REFERENCES account(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Successful interactive AI results owned by website users';
