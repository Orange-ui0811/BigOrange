CREATE TABLE IF NOT EXISTS pricing_change_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    service_key VARCHAR(64) NOT NULL,
    old_cost INT NOT NULL,
    new_cost INT NOT NULL,
    admin_id BIGINT NULL,
    changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_pricing_change_log_service_time (service_key, changed_at),
    KEY idx_pricing_change_log_admin_time (admin_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO ai_service_pricing (service_key, service_name, segment_cost)
VALUES
    ('reward:work-approved', '作品审核通过奖励', 50),
    ('reward:job-approved', '岗位投稿审核通过奖励', 50),
    ('reward:competition-approved', '比赛投稿审核通过奖励', 20);
