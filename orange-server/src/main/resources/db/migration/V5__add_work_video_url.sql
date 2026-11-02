-- Optional showcase video URL for published works. Existing works remain unchanged.
SET @has_video_url = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'work' AND column_name = 'video_url');
SET @sql = IF(@has_video_url = 0,
  'ALTER TABLE work ADD COLUMN video_url VARCHAR(512) NULL COMMENT ''作品视频展示链接'' AFTER demo_url',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
