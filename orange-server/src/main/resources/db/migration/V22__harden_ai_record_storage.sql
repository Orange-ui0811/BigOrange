-- Harden AI record storage without rewriting legacy records.

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'trend_history'
       AND column_name = 'custom_topics') = 0,
    'ALTER TABLE trend_history ADD COLUMN custom_topics VARCHAR(200) NULL AFTER time_commitment',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'trend_history'
       AND column_name = 'custom_purpose') = 0,
    'ALTER TABLE trend_history ADD COLUMN custom_purpose VARCHAR(200) NULL AFTER custom_topics',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'trend_history'
       AND column_name = 'custom_exp') = 0,
    'ALTER TABLE trend_history ADD COLUMN custom_exp VARCHAR(200) NULL AFTER custom_purpose',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'trend_history'
       AND column_name = 'custom_lang') = 0,
    'ALTER TABLE trend_history ADD COLUMN custom_lang VARCHAR(200) NULL AFTER custom_exp',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'trend_history'
       AND column_name = 'custom_time') = 0,
    'ALTER TABLE trend_history ADD COLUMN custom_time VARCHAR(200) NULL AFTER custom_lang',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'trend_history'
       AND column_name = 'use_rag') = 0,
    'ALTER TABLE trend_history ADD COLUMN use_rag TINYINT(1) NOT NULL DEFAULT 1 AFTER custom_time',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE trend_history
    MODIFY topics TEXT NULL,
    MODIFY purpose VARCHAR(200) NULL,
    MODIFY experience VARCHAR(200) NULL,
    MODIFY languages TEXT NULL,
    MODIFY time_commitment VARCHAR(200) NULL;

DELETE history
FROM trend_history history
LEFT JOIN account owner ON owner.id = history.account_id
WHERE history.account_id IS NOT NULL AND owner.id IS NULL;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.referential_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'trend_history'
       AND constraint_name = 'fk_trend_history_account') = 0,
    'ALTER TABLE trend_history ADD CONSTRAINT fk_trend_history_account FOREIGN KEY (account_id) REFERENCES account(id) ON DELETE CASCADE',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE plan_history MODIFY summary TEXT NULL;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'plan_history'
       AND column_name = 'content_hash') = 0,
    'ALTER TABLE plan_history ADD COLUMN content_hash CHAR(64) NULL AFTER account_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'plan_history'
       AND index_name = 'uk_plan_account_content') = 0,
    'ALTER TABLE plan_history ADD UNIQUE KEY uk_plan_account_content (account_id, content_hash)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
