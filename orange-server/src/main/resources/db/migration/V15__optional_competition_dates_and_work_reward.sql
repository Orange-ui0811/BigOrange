ALTER TABLE ai_competition
    MODIFY COLUMN start_time DATETIME NULL,
    MODIFY COLUMN end_time DATETIME NULL;

SET @work_reward_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'work'
      AND column_name = 'approval_reward_granted'
);

SET @work_reward_sql = IF(
    @work_reward_column_exists = 0,
    'ALTER TABLE work ADD COLUMN approval_reward_granted TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''作品首次审核通过奖励是否已发放'' AFTER publish_status',
    'SELECT 1'
);

PREPARE work_reward_stmt FROM @work_reward_sql;
EXECUTE work_reward_stmt;
DEALLOCATE PREPARE work_reward_stmt;
