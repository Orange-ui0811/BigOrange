CREATE TABLE IF NOT EXISTS leaderboard_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source VARCHAR(16) NOT NULL COMMENT '数据源: github',
    topic_name VARCHAR(64) NULL COMMENT '分类 topic，NULL 表示全部分类',
    period VARCHAR(16) NOT NULL DEFAULT 'all' COMMENT '时间窗口: all / week',
    snapshot_date DATE NOT NULL COMMENT '快照日期',
    data_json MEDIUMTEXT NOT NULL COMMENT '榜单 JSON',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_snapshot (source, topic_name, period, snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='榜单快照表';

CREATE TABLE IF NOT EXISTS storage_cleanup_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    object_key VARCHAR(512) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(1000) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_storage_cleanup_object (object_key),
    KEY idx_storage_cleanup_retry (next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='存储对象删除重试队列';
