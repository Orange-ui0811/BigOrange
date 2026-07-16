-- Align databases created by historical/manual scripts with the current runtime model.

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='account' AND column_name='phone')=0,
  'ALTER TABLE account ADD COLUMN phone VARCHAR(32) NULL AFTER password', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='account' AND column_name='email')=0,
  'ALTER TABLE account ADD COLUMN email VARCHAR(128) NULL AFTER phone', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='account' AND column_name='quota')=0,
  'ALTER TABLE account ADD COLUMN quota BIGINT NOT NULL DEFAULT 0 AFTER email', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='account' AND index_name='uk_email')=0,
  'ALTER TABLE account ADD UNIQUE KEY uk_email (email)', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='category' AND column_name='topic_name')=0,
  'ALTER TABLE category ADD COLUMN topic_name VARCHAR(64) NULL AFTER name', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='knowledge_document' AND column_name='content')=0,
  'ALTER TABLE knowledge_document ADD COLUMN content MEDIUMTEXT NULL AFTER file_size', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trend_history' AND column_name='account_id')=0,
  'ALTER TABLE trend_history ADD COLUMN account_id BIGINT NULL AFTER id', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trend_history' AND column_name='browser_id' AND is_nullable='NO')=1,
  'ALTER TABLE trend_history MODIFY browser_id VARCHAR(64) NULL', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

CREATE TABLE IF NOT EXISTS quota_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL,
    amount INT NOT NULL,
    balance BIGINT NOT NULL,
    source VARCHAR(32),
    description VARCHAR(256),
    request_id VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_quota_account (account_id), KEY idx_quota_create_time (create_time),
    UNIQUE KEY uk_quota_request_id (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS top_up (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    amount INT NOT NULL,
    money DECIMAL(10,2) NOT NULL,
    trade_no VARCHAR(64) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    payment_provider VARCHAR(32) NOT NULL,
    provider_trade_no VARCHAR(128),
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    complete_time DATETIME,
    PRIMARY KEY (id), UNIQUE KEY uk_trade_no (trade_no), KEY idx_top_up_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='top_up' AND column_name='provider_trade_no')=0,
  'ALTER TABLE top_up ADD COLUMN provider_trade_no VARCHAR(128) NULL AFTER payment_provider', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='top_up' AND index_name='uk_top_up_provider_trade_no')=0,
  'ALTER TABLE top_up ADD UNIQUE KEY uk_top_up_provider_trade_no (provider_trade_no)', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

CREATE TABLE IF NOT EXISTS payment_callback_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider VARCHAR(32) NOT NULL,
    provider_event_id VARCHAR(192) NOT NULL,
    trade_no VARCHAR(64) NOT NULL,
    provider_trade_no VARCHAR(128) NOT NULL,
    trade_status VARCHAR(32) NOT NULL,
    payload JSON NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_event (provider, provider_event_id),
    KEY idx_payment_event_trade_no (trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS redemption_code (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(48) NOT NULL,
    quota INT NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL,
    used_by BIGINT,
    used_time DATETIME,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_code (code), KEY idx_redemption_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS plan_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    summary VARCHAR(512),
    phase1 MEDIUMTEXT, phase2 MEDIUMTEXT, phase3 MEDIUMTEXT,
    phase4 MEDIUMTEXT, phase5 MEDIUMTEXT, phase6 MEDIUMTEXT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_plan_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS job_posting (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company VARCHAR(128) NOT NULL,
    position VARCHAR(128) NOT NULL,
    direction VARCHAR(128),
    content MEDIUMTEXT,
    display_content MEDIUMTEXT,
    audit_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    submitter_id BIGINT,
    submitter_ip VARCHAR(64),
    submitter_role VARCHAR(16),
    reject_reason VARCHAR(512),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_job_status (audit_status), KEY idx_job_direction (direction),
    KEY idx_job_company_position (company, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
