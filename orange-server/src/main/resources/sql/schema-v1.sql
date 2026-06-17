-- ============================================================
-- OrangeJD Platform — Schema v1 (全量设计)
-- ============================================================
-- 建表策略：全量设计保证模型一致性，分阶段验证执行。
-- Phase 1-2 先落地验证；Phase 3-6 表在各自实现阶段二次确认后执行。
-- ============================================================

-- ============================================================
-- Phase 1: 账号体系
-- ============================================================

CREATE TABLE IF NOT EXISTS account (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    username    VARCHAR(64)     NOT NULL COMMENT '用户名',
    password    VARCHAR(256)    NOT NULL COMMENT '密码密文（BCrypt）',
    status      VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态: ACTIVE / DISABLED',
    role        VARCHAR(16)     NOT NULL DEFAULT 'USER' COMMENT '角色: ADMIN / USER',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_status (status),
    KEY idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号表';

CREATE TABLE IF NOT EXISTS user_profile (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    account_id       BIGINT      NOT NULL COMMENT '关联 account.id',
    nickname         VARCHAR(64)          COMMENT '昵称',
    avatar_url       VARCHAR(512)         COMMENT '头像URL',
    bio              VARCHAR(512)         COMMENT '个人简介',
    skills           VARCHAR(512)         COMMENT '技能标签',
    contact_intro    VARCHAR(512)         COMMENT '联系方式简介',
    homepage_visible TINYINT     NOT NULL DEFAULT 1 COMMENT '主页可见: 1可见 / 0隐藏',
    create_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_id (account_id),
    CONSTRAINT fk_profile_account FOREIGN KEY (account_id) REFERENCES account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户资料表';

-- 初始管理员账号（密码 123456 的 BCrypt 密文）
INSERT INTO account (username, password, status, role) VALUES
    ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5Eh', 'ACTIVE', 'ADMIN');


-- ============================================================
-- Phase 2: 基础数据（分类体系）
-- ============================================================

CREATE TABLE IF NOT EXISTS category (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64) NOT NULL COMMENT '分类名称',
    type        VARCHAR(16) NOT NULL COMMENT '分类类型: WORK / REQUIREMENT',
    sort        INT         NOT NULL DEFAULT 0 COMMENT '排序值（越小越靠前）',
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE / DISABLED',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_type (type),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分类表';


-- ============================================================
-- Phase 3: 内容发布（作品 + 需求 + 审核）
-- ⚠️ 以下表 Phase 3 实现时执行
-- ============================================================

CREATE TABLE IF NOT EXISTS work (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id         BIGINT       NOT NULL COMMENT '作者 account.id',
    title            VARCHAR(128) NOT NULL COMMENT '作品标题',
    summary          VARCHAR(512)          COMMENT '简介',
    detail           TEXT                  COMMENT '详情（富文本）',
    category_id      BIGINT                COMMENT '分类 category.id',
    tags             VARCHAR(512)          COMMENT '标签（逗号分隔）',
    cover_url        VARCHAR(512)          COMMENT '封面图URL',
    demo_url         VARCHAR(512)          COMMENT '演示链接',
    audit_status     VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '审核状态: DRAFT / PENDING_REVIEW / APPROVED / REJECTED',
    visibility_status VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性: PUBLIC / PRIVATE',
    publish_status   VARCHAR(16)  NOT NULL DEFAULT 'UNPUBLISHED' COMMENT '发布状态: PUBLISHED / UNPUBLISHED',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_owner_id (owner_id),
    KEY idx_category_id (category_id),
    KEY idx_audit_status (audit_status),
    KEY idx_visibility_status (visibility_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作品表';

CREATE TABLE IF NOT EXISTS work_sale_option (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    work_id     BIGINT          NOT NULL COMMENT '关联 work.id',
    sale_type   VARCHAR(32)     NOT NULL COMMENT '售卖类型: FIXED_PRICE / CUSTOM / DEPLOY_SERVICE',
    price       DECIMAL(12,2)   NOT NULL COMMENT '价格',
    description VARCHAR(512)             COMMENT '售卖说明',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_work_id (work_id),
    CONSTRAINT fk_sale_option_work FOREIGN KEY (work_id) REFERENCES work(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作品售卖项表';

CREATE TABLE IF NOT EXISTS requirement (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    publisher_id      BIGINT          NOT NULL COMMENT '发布者 account.id',
    title             VARCHAR(128)    NOT NULL COMMENT '需求标题',
    detail            TEXT                     COMMENT '需求详情',
    category_id       BIGINT                   COMMENT '分类 category.id',
    budget_min        DECIMAL(12,2)            COMMENT '预算下限',
    budget_max        DECIMAL(12,2)            COMMENT '预算上限',
    audit_status      VARCHAR(32)     NOT NULL DEFAULT 'DRAFT' COMMENT '审核状态',
    requirement_status VARCHAR(16)    NOT NULL DEFAULT 'OPEN' COMMENT '需求状态: OPEN / IN_PROGRESS / CLOSED',
    visibility_status VARCHAR(16)     NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性: PUBLIC / PRIVATE',
    create_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_publisher_id (publisher_id),
    KEY idx_category_id (category_id),
    KEY idx_audit_status (audit_status),
    KEY idx_requirement_status (requirement_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求表';

CREATE TABLE IF NOT EXISTS audit_record (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(16) NOT NULL COMMENT '审核目标类型: WORK / REQUIREMENT',
    target_id   BIGINT      NOT NULL COMMENT '审核目标ID',
    auditor_id  BIGINT               COMMENT '审核人 account.id',
    audit_status VARCHAR(16) NOT NULL COMMENT '审核结果: APPROVED / REJECTED',
    reason      VARCHAR(512)         COMMENT '审核原因/意见',
    audit_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_target (target_type, target_id),
    KEY idx_auditor_id (auditor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核记录表';


-- ============================================================
-- Phase 4: 内容发现（收藏 + 评论 + 浏览历史）
-- ⚠️ 以下表 Phase 4 实现时执行
-- ============================================================

CREATE TABLE IF NOT EXISTS favorite (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    account_id  BIGINT      NOT NULL COMMENT '用户ID',
    target_type VARCHAR(16) NOT NULL COMMENT '目标类型: WORK / REQUIREMENT',
    target_id   BIGINT      NOT NULL COMMENT '目标ID',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorite (account_id, target_type, target_id),
    KEY idx_account_id (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏表';

CREATE TABLE IF NOT EXISTS comment (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    account_id  BIGINT       NOT NULL COMMENT '评论者ID',
    target_type VARCHAR(16)  NOT NULL COMMENT '目标类型: WORK / REQUIREMENT',
    target_id   BIGINT       NOT NULL COMMENT '目标ID',
    content     VARCHAR(1024) NOT NULL COMMENT '评论内容',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_target (target_type, target_id),
    KEY idx_account_id (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

CREATE TABLE IF NOT EXISTS browse_history (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    account_id  BIGINT      NOT NULL COMMENT '用户ID',
    target_type VARCHAR(16) NOT NULL COMMENT '目标类型: WORK / REQUIREMENT',
    target_id   BIGINT      NOT NULL COMMENT '目标ID',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_account_time (account_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览历史表';


-- ============================================================
-- Phase 5: 沟通协商（会话 + 消息 + 通知）
-- ⚠️ 以下表 Phase 5 实现时执行
-- ============================================================

CREATE TABLE IF NOT EXISTS conversation (
    id              BIGINT   NOT NULL AUTO_INCREMENT,
    work_id         BIGINT            COMMENT '关联作品ID',
    requirement_id  BIGINT            COMMENT '关联需求ID',
    initiator_id    BIGINT   NOT NULL COMMENT '发起方 account.id',
    receiver_id     BIGINT   NOT NULL COMMENT '接收方 account.id',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_initiator_id (initiator_id),
    KEY idx_receiver_id (receiver_id),
    KEY idx_work_id (work_id),
    KEY idx_requirement_id (requirement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

CREATE TABLE IF NOT EXISTS message (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT        NOT NULL COMMENT '关联会话ID',
    sender_id       BIGINT        NOT NULL COMMENT '发送方 account.id',
    content         VARCHAR(2048) NOT NULL COMMENT '消息内容',
    attachment_url  VARCHAR(512)           COMMENT '附件URL',
    is_read         TINYINT       NOT NULL DEFAULT 0 COMMENT '是否已读: 1已读 / 0未读',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conversation_id (conversation_id),
    KEY idx_sender_id (sender_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

CREATE TABLE IF NOT EXISTS notification (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    account_id  BIGINT       NOT NULL COMMENT '接收方 account.id',
    message     VARCHAR(512) NOT NULL COMMENT '通知内容',
    type        VARCHAR(32)  NOT NULL COMMENT '通知类型: AUDIT / MESSAGE / QUOTE / ORDER / SYSTEM',
    related_id  BIGINT                COMMENT '关联业务ID',
    is_read     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读: 1已读 / 0未读',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_account_read (account_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知表';


-- ============================================================
-- Phase 6: 交易闭环（报价单 + 订单 + 支付阶段 + 交付）
-- ⚠️ 以下表 Phase 6 实现时执行
-- ============================================================

CREATE TABLE IF NOT EXISTS quote (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT          NOT NULL COMMENT '关联会话ID',
    sender_id       BIGINT          NOT NULL COMMENT '发起方（报价方）',
    receiver_id     BIGINT          NOT NULL COMMENT '接收方',
    price           DECIMAL(12,2)   NOT NULL COMMENT '报价金额',
    description     VARCHAR(512)             COMMENT '报价说明',
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING_CONFIRM' COMMENT '状态: PENDING_CONFIRM / ACCEPTED / REJECTED / CANCELLED / EXPIRED',
    expire_time     DATETIME                 COMMENT '过期时间',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conversation_id (conversation_id),
    KEY idx_receiver_id (receiver_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报价单表';

CREATE TABLE IF NOT EXISTS `order` (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    source_type VARCHAR(16)     NOT NULL COMMENT '订单来源: QUOTE / WORK',
    source_id   BIGINT          NOT NULL COMMENT '来源ID（quote.id 或 work.id）',
    buyer_id    BIGINT          NOT NULL COMMENT '买方 account.id',
    seller_id   BIGINT          NOT NULL COMMENT '卖方 account.id',
    work_id     BIGINT                   COMMENT '关联作品ID',
    deposit     DECIMAL(12,2)            COMMENT '定金金额',
    final_amount DECIMAL(12,2)           COMMENT '尾款金额',
    total_amount DECIMAL(12,2)  NOT NULL COMMENT '订单总金额',
    order_status VARCHAR(32)    NOT NULL DEFAULT 'CREATED' COMMENT '订单状态',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_buyer_id (buyer_id),
    KEY idx_seller_id (seller_id),
    KEY idx_work_id (work_id),
    KEY idx_order_status (order_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

CREATE TABLE IF NOT EXISTS payment_stage (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    order_id    BIGINT          NOT NULL COMMENT '关联订单ID',
    stage_type  VARCHAR(16)     NOT NULL COMMENT '阶段类型: DEPOSIT / FINAL',
    amount      DECIMAL(12,2)   NOT NULL COMMENT '支付金额',
    pay_status  VARCHAR(16)     NOT NULL DEFAULT 'UNPAID' COMMENT '支付状态: UNPAID / PAID',
    pay_time    DATETIME                 COMMENT '支付时间',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES `order`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付阶段表';

CREATE TABLE IF NOT EXISTS delivery (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    order_id      BIGINT       NOT NULL COMMENT '关联订单ID',
    deliverer_id  BIGINT       NOT NULL COMMENT '交付方 account.id',
    content       VARCHAR(2048) NOT NULL COMMENT '交付内容描述',
    attachment_url VARCHAR(512)          COMMENT '交付附件URL',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    CONSTRAINT fk_delivery_order FOREIGN KEY (order_id) REFERENCES `order`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交付记录表';


-- ============================================================
-- 榜单功能: 分类迁移（2026-06-17）
-- ============================================================

-- Step 1.2: 新增 topic_name 字段
ALTER TABLE category ADD COLUMN IF NOT EXISTS topic_name VARCHAR(64) DEFAULT NULL COMMENT 'GitHub topic名称（英文）' AFTER name;

-- Step 1.3: 插入 14 个新 Topic 分类（四大领域）
INSERT INTO category (name, topic_name, type, sort, status) VALUES
('Creative Coding',  'creative-coding',    'WORK', 1, 'ACTIVE'),
('Game Development', 'game-development',   'WORK', 2, 'ACTIVE'),
('WebGL',            'WebGL',              'WORK', 3, 'ACTIVE'),
('Canvas',           'Canvas',             'WORK', 4, 'ACTIVE'),
('Web Audio',        'Web-Audio',          'WORK', 5, 'ACTIVE'),
('AI Agents',        'ai-agents',          'WORK', 6, 'ACTIVE'),
('RAG',              'RAG',                'WORK', 7, 'ACTIVE'),
('Self-Hosted',      'self-hosted',        'WORK', 8, 'ACTIVE'),
('Home Assistant',   'home-assistant',     'WORK', 9, 'ACTIVE'),
('Awesome Self-Host','awesome-self-hosted','WORK',10, 'ACTIVE'),
('ESP32',            'ESP32',              'WORK',11, 'ACTIVE'),
('Raspberry Pi',     'raspberry-pi',       'WORK',12, 'ACTIVE'),
('Home Automation',  'home-automation',    'WORK',13, 'ACTIVE'),
('3D Printing',      '3d-printing',        'WORK',14, 'ACTIVE'),
('Other',            'other',              'WORK',15, 'ACTIVE');

-- Step 1.4: 停用旧分类（topic_name 为空的记录）
UPDATE category SET status = 'INACTIVE' WHERE topic_name IS NULL AND status = 'ACTIVE';


-- ============================================================
-- 榜单快照表（GitHub 数据每日凌晨刷新，保留 7 天）
-- ============================================================

CREATE TABLE IF NOT EXISTS leaderboard_snapshot (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    source        VARCHAR(16)  NOT NULL COMMENT '数据源: github',
    topic_name    VARCHAR(64)           COMMENT '分类 topic（NULL=全部分类）',
    period        VARCHAR(16)  NOT NULL DEFAULT 'all' COMMENT '时间窗口: all / week',
    snapshot_date DATE         NOT NULL COMMENT '快照日期',
    data_json     MEDIUMTEXT   NOT NULL COMMENT '榜单 JSON',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_snapshot (source, topic_name, period, snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='榜单快照表';
