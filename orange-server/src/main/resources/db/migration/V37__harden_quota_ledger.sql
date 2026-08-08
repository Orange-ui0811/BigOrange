ALTER TABLE ai_service_pricing
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1 AFTER segment_cost;

ALTER TABLE ai_result_record
    ADD COLUMN request_hash CHAR(64) NOT NULL DEFAULT '' AFTER request_id;

CREATE TABLE IF NOT EXISTS quota_operation (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    operation_no         VARCHAR(64)  NOT NULL COMMENT 'OVCQ_ prefixed public-safe quota operation number',
    account_id           BIGINT       NOT NULL,
    idempotency_scope    VARCHAR(64)  NOT NULL,
    request_id           VARCHAR(128) NOT NULL,
    idempotency_key_hash CHAR(64)     NULL COMMENT 'SHA-256 only; raw client key is never stored',
    request_hash         CHAR(64)     NOT NULL COMMENT 'SHA-256 of canonical request payload',
    service_key          VARCHAR(64)  NULL,
    price_version        BIGINT       NULL,
    amount               BIGINT       NOT NULL,
    status               VARCHAR(16)  NOT NULL COMMENT 'CONSUMED/CREDITED/REFUNDED',
    debit_log_id         BIGINT       NULL,
    refund_log_id        BIGINT       NULL,
    create_time          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quota_operation_no (operation_no),
    UNIQUE KEY uk_quota_operation_request (request_id),
    UNIQUE KEY uk_quota_operation_idempotency
        (account_id, idempotency_scope, idempotency_key_hash),
    KEY idx_quota_operation_account_time (account_id, create_time),
    KEY idx_quota_operation_status_time (status, create_time),
    CONSTRAINT fk_quota_operation_account FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT chk_quota_operation_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Idempotent quota business operations; monetary payments remain separate';

ALTER TABLE quota_log
    MODIFY COLUMN amount BIGINT NOT NULL,
    MODIFY COLUMN request_id VARCHAR(128) NULL,
    ADD COLUMN transaction_no VARCHAR(64) NULL AFTER id,
    ADD COLUMN operation_id BIGINT NULL AFTER transaction_no,
    ADD COLUMN original_log_id BIGINT NULL AFTER operation_id,
    ADD COLUMN service_key VARCHAR(64) NULL AFTER source,
    ADD COLUMN price_version BIGINT NULL AFTER service_key,
    ADD COLUMN request_hash CHAR(64) NULL AFTER request_id,
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'POSTED' AFTER request_hash;

UPDATE quota_log
SET transaction_no = CONCAT('OVCQ_', LEFT(SHA2(CONCAT('legacy:', id, ':', create_time), 256), 32))
WHERE transaction_no IS NULL;

ALTER TABLE quota_log
    MODIFY COLUMN transaction_no VARCHAR(64) NOT NULL,
    ADD UNIQUE KEY uk_quota_transaction_no (transaction_no),
    ADD KEY idx_quota_operation_id (operation_id),
    ADD KEY idx_quota_original_log_id (original_log_id),
    ADD CONSTRAINT fk_quota_log_operation FOREIGN KEY (operation_id) REFERENCES quota_operation(id),
    ADD CONSTRAINT fk_quota_log_original FOREIGN KEY (original_log_id) REFERENCES quota_log(id);
