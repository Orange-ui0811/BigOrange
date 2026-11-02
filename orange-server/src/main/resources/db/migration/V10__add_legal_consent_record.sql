-- Persist the legal document version accepted during registration.
SET @has_legal_version = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'account' AND column_name = 'legal_version');
SET @sql = IF(@has_legal_version = 0,
  'ALTER TABLE account ADD COLUMN legal_version VARCHAR(16) NULL COMMENT ''注册时同意的协议版本'' AFTER role',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_legal_accepted_time = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'account' AND column_name = 'legal_accepted_time');
SET @sql = IF(@has_legal_accepted_time = 0,
  'ALTER TABLE account ADD COLUMN legal_accepted_time DATETIME NULL COMMENT ''协议同意时间'' AFTER legal_version',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
