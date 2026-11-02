CREATE TABLE agent_device_link_code (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    code_hash CHAR(64) NOT NULL,
    device_name VARCHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    consumed_at DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_device_link_code_hash (code_hash),
    KEY idx_agent_device_link_code_account (account_id, create_time),
    KEY idx_agent_device_link_code_expiry (expires_at),
    CONSTRAINT fk_agent_device_link_code_account FOREIGN KEY (account_id) REFERENCES account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能体设备一次性绑定码';

CREATE TABLE agent_device_credential (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    device_name VARCHAR(64) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    token_hint VARCHAR(12) NOT NULL,
    scopes VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    last_used_at DATETIME NULL,
    expires_at DATETIME NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_device_credential_token (token_hash),
    KEY idx_agent_device_credential_account (account_id, status, update_time),
    KEY idx_agent_device_credential_expiry (expires_at),
    CONSTRAINT fk_agent_device_credential_account FOREIGN KEY (account_id) REFERENCES account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能体设备凭据';

CREATE TABLE agent_action_task (
    id CHAR(36) NOT NULL,
    account_id BIGINT NOT NULL,
    credential_id BIGINT NOT NULL,
    action_key VARCHAR(64) NOT NULL,
    parameters_json JSON NOT NULL,
    action_summary VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL,
    quota_cost INT NOT NULL DEFAULT 0,
    balance_snapshot BIGINT NOT NULL DEFAULT 0,
    result_json JSON NULL,
    spoken_summary VARCHAR(500) NULL,
    error_code VARCHAR(64) NULL,
    expires_at DATETIME NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_agent_action_account_time (account_id, create_time),
    KEY idx_agent_action_credential_time (credential_id, create_time),
    KEY idx_agent_action_status_time (status, update_time),
    CONSTRAINT fk_agent_action_account FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT fk_agent_action_credential FOREIGN KEY (credential_id) REFERENCES agent_device_credential(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能体受控动作任务';
