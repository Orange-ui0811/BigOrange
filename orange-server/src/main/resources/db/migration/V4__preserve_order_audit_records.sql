-- Completed financial records must be hidden per participant rather than physically deleted.
SET @has_buyer_deleted = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'order' AND column_name = 'buyer_deleted');
SET @sql = IF(@has_buyer_deleted = 0,
  'ALTER TABLE `order` ADD COLUMN buyer_deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''买方列表隐藏'' AFTER order_status',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_seller_deleted = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'order' AND column_name = 'seller_deleted');
SET @sql = IF(@has_seller_deleted = 0,
  'ALTER TABLE `order` ADD COLUMN seller_deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''卖方列表隐藏'' AFTER buyer_deleted',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
