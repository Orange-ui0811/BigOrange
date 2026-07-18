ALTER TABLE delivery
    ADD COLUMN attachment_name VARCHAR(255) NULL COMMENT '附件原始文件名' AFTER attachment_url,
    ADD COLUMN attachment_content_type VARCHAR(128) NULL COMMENT '附件媒体类型' AFTER attachment_name,
    ADD COLUMN attachment_size BIGINT NULL COMMENT '附件大小（字节）' AFTER attachment_content_type;

CREATE TABLE storage_cleanup_job (
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
