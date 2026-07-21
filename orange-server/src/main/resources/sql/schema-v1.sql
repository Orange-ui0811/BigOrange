-- ============================================================
-- OrangeJD Platform — Schema v1 (全量设计)
-- ============================================================
-- 建表策略：全量设计保证模型一致性，分阶段验证执行。
-- Phase 1-2 先落地验证；Phase 3-6 表在各自实现阶段二次确认后执行。
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- Phase 1: 账号体系
-- ============================================================

CREATE TABLE IF NOT EXISTS account (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    username    VARCHAR(64)     NOT NULL COMMENT '用户名',
    password    VARCHAR(256)    NOT NULL COMMENT '密码密文（BCrypt）',
    phone       VARCHAR(32)              COMMENT '手机号（兼容旧账号）',
    email       VARCHAR(128)             COMMENT '绑定邮箱',
    quota       BIGINT          NOT NULL DEFAULT 0 COMMENT '可用配额（segments）',
    status      VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态: ACTIVE / DISABLED',
    role        VARCHAR(16)     NOT NULL DEFAULT 'USER' COMMENT '角色: ADMIN / USER',
    legal_version VARCHAR(16)            COMMENT '注册时同意的协议版本',
    legal_accepted_time DATETIME         COMMENT '协议同意时间',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email),
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
    ('admin', '$2y$10$51sct1ovTDC5ET7ad.XX/eM7Y7s.GOl9EoiehUOx.kLgiusX8AOeW', 'ACTIVE', 'ADMIN');


-- ============================================================
-- Phase 2: 基础数据（分类体系）
-- ============================================================

CREATE TABLE IF NOT EXISTS category (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64) NOT NULL COMMENT '分类名称',
    topic_name  VARCHAR(64)          COMMENT 'GitHub topic名称（英文）',
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
    video_url        VARCHAR(512)          COMMENT '作品视频文件地址',
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
    buyer_deleted TINYINT       NOT NULL DEFAULT 0 COMMENT '买方列表隐藏',
    seller_deleted TINYINT      NOT NULL DEFAULT 0 COMMENT '卖方列表隐藏',
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
    attachment_name VARCHAR(255)         COMMENT '附件原始文件名',
    attachment_content_type VARCHAR(128) COMMENT '附件媒体类型',
    attachment_size BIGINT               COMMENT '附件大小（字节）',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    CONSTRAINT fk_delivery_order FOREIGN KEY (order_id) REFERENCES `order`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交付记录表';

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


-- ============================================================
-- 榜单功能: 分类迁移（2026-06-17）
-- ============================================================

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

CREATE TABLE IF NOT EXISTS trend_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    topics VARCHAR(512),
    purpose VARCHAR(128),
    experience VARCHAR(128),
    languages VARCHAR(512),
    time_commitment VARCHAR(128),
    result_json MEDIUMTEXT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_trend_account_id (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 知识库（RAG 趋势分析增强）
-- ============================================================

CREATE TABLE IF NOT EXISTS knowledge_document (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    title        VARCHAR(256) NOT NULL COMMENT '文档标题（文件名或仓库名）',
    source       VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源: MANUAL / GITHUB',
    source_ref   VARCHAR(512)          COMMENT '来源引用（GitHub full_name 或文件路径，幂等键）',
    chunk_count  INT          NOT NULL DEFAULT 0 COMMENT '切片数',
    file_size    BIGINT                COMMENT '原始文件大小（字节）',
    content      MEDIUMTEXT            COMMENT '原始文档内容',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_source (source),
    UNIQUE KEY uk_source_ref (source_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';

-- ============================================================
-- Segments 配额与支付账务
-- ============================================================

CREATE TABLE IF NOT EXISTS quota_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    account_id   BIGINT       NOT NULL COMMENT '用户ID',
    type         VARCHAR(16)  NOT NULL COMMENT 'CONSUME/RECHARGE/REFUND/ADMIN',
    amount       INT          NOT NULL COMMENT '变动量（正=加，负=减）',
    balance      BIGINT       NOT NULL COMMENT '变动后余额',
    source       VARCHAR(32)           COMMENT 'REDEMPTION/EPAY/REGISTER/MANUAL',
    description  VARCHAR(256)          COMMENT '操作描述',
    request_id   VARCHAR(64)           COMMENT '业务幂等键',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_quota_account (account_id),
    KEY idx_quota_create_time (create_time),
    UNIQUE KEY uk_quota_request_id (request_id),
    CONSTRAINT fk_quota_account FOREIGN KEY (account_id) REFERENCES account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不可变配额流水';

CREATE TABLE IF NOT EXISTS top_up (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    account_id         BIGINT        NOT NULL COMMENT '用户ID',
    amount             INT           NOT NULL COMMENT '配额数（segments）',
    money              DECIMAL(10,2) NOT NULL COMMENT '订单金额（元）',
    trade_no           VARCHAR(64)   NOT NULL COMMENT '本地订单号',
    payment_method     VARCHAR(32)   NOT NULL COMMENT 'wxpay/alipay',
    payment_provider   VARCHAR(32)   NOT NULL COMMENT '支付渠道',
    provider_trade_no  VARCHAR(128)           COMMENT '支付渠道流水号',
    status             VARCHAR(16)   NOT NULL DEFAULT 'pending',
    create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    complete_time      DATETIME               COMMENT '完成时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_top_up_trade_no (trade_no),
    UNIQUE KEY uk_top_up_provider_trade_no (provider_trade_no),
    KEY idx_top_up_account (account_id),
    KEY idx_top_up_status_time (status, create_time),
    CONSTRAINT fk_top_up_account FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT chk_top_up_amount CHECK (amount > 0),
    CONSTRAINT chk_top_up_money CHECK (money > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充值支付订单';

CREATE TABLE IF NOT EXISTS payment_callback_event (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    provider            VARCHAR(32)  NOT NULL,
    provider_event_id   VARCHAR(192) NOT NULL COMMENT '渠道事件幂等键',
    trade_no            VARCHAR(64)  NOT NULL,
    provider_trade_no   VARCHAR(128) NOT NULL,
    trade_status        VARCHAR(32)  NOT NULL,
    payload             JSON         NOT NULL COMMENT '已移除签名的原始回调',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_event (provider, provider_event_id),
    KEY idx_payment_event_trade_no (trade_no),
    CONSTRAINT fk_payment_event_top_up FOREIGN KEY (trade_no) REFERENCES top_up(trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付回调审计事件';

CREATE TABLE IF NOT EXISTS order_payment (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    trade_no           VARCHAR(64)   NOT NULL COMMENT '本地订单支付流水号',
    order_id           BIGINT        NOT NULL,
    payment_stage_id   BIGINT        NOT NULL,
    payer_account_id   BIGINT        NOT NULL,
    stage_type         VARCHAR(16)   NOT NULL COMMENT 'DEPOSIT/FINAL',
    amount             DECIMAL(12,2) NOT NULL,
    payment_method     VARCHAR(32)   NOT NULL COMMENT 'wxpay/alipay',
    payment_provider   VARCHAR(32)   NOT NULL DEFAULT 'epay',
    provider_trade_no  VARCHAR(128)           COMMENT '支付渠道流水号',
    status             VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    complete_time      DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_payment_trade_no (trade_no),
    UNIQUE KEY uk_order_payment_stage (payment_stage_id),
    UNIQUE KEY uk_order_payment_provider_trade (payment_provider, provider_trade_no),
    KEY idx_order_payment_order (order_id, stage_type),
    KEY idx_order_payment_status_time (status, create_time),
    CONSTRAINT fk_order_payment_order FOREIGN KEY (order_id) REFERENCES `order`(id),
    CONSTRAINT fk_order_payment_stage FOREIGN KEY (payment_stage_id) REFERENCES payment_stage(id),
    CONSTRAINT fk_order_payment_payer FOREIGN KEY (payer_account_id) REFERENCES account(id),
    CONSTRAINT chk_order_payment_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品订单真实支付流水';

CREATE TABLE IF NOT EXISTS order_payment_callback_event (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    provider            VARCHAR(32)  NOT NULL,
    provider_event_id   VARCHAR(192) NOT NULL,
    trade_no            VARCHAR(64)  NOT NULL,
    provider_trade_no   VARCHAR(128) NOT NULL,
    trade_status        VARCHAR(32)  NOT NULL,
    payload             JSON         NOT NULL COMMENT '不含签名和密钥的回调审计信息',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_payment_event (provider, provider_event_id),
    KEY idx_order_payment_event_trade (trade_no),
    CONSTRAINT fk_order_payment_event_trade FOREIGN KEY (trade_no) REFERENCES order_payment(trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品订单支付回调审计事件';

CREATE TABLE IF NOT EXISTS redemption_code (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    code         VARCHAR(48)  NOT NULL COMMENT '充值码',
    quota        INT          NOT NULL COMMENT '所含配额',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1未使用 2禁用 3已使用',
    created_by   BIGINT       NOT NULL COMMENT '管理员ID',
    used_by      BIGINT                COMMENT '使用者ID',
    used_time    DATETIME              COMMENT '使用时间',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_redemption_code (code),
    KEY idx_redemption_status (status),
    CONSTRAINT chk_redemption_quota CHECK (quota > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充值码';

CREATE TABLE IF NOT EXISTS plan_history (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    account_id  BIGINT      NOT NULL,
    summary     VARCHAR(512),
    phase1      MEDIUMTEXT,
    phase2      MEDIUMTEXT,
    phase3      MEDIUMTEXT,
    phase4      MEDIUMTEXT,
    phase5      MEDIUMTEXT,
    phase6      MEDIUMTEXT,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_plan_account (account_id),
    CONSTRAINT fk_plan_account FOREIGN KEY (account_id) REFERENCES account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI方案历史';

CREATE TABLE IF NOT EXISTS job_posting (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    company         VARCHAR(128)  NOT NULL,
    position        VARCHAR(128)  NOT NULL,
    direction       VARCHAR(128),
    content         MEDIUMTEXT,
    display_content MEDIUMTEXT,
    audit_status    VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    submitter_id    BIGINT,
    submitter_ip    VARCHAR(64),
    submitter_role  VARCHAR(16),
    reject_reason   VARCHAR(512),
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_job_status (audit_status),
    KEY idx_job_direction (direction),
    KEY idx_job_company_position (company, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位投稿';

-- ============================================================
-- Phase 7: AI 服务消费定价管理
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_service_pricing (
    service_key  VARCHAR(64)   NOT NULL COMMENT '服务标识，如 ai:trend:v1',
    service_name VARCHAR(128)  NOT NULL COMMENT '显示名，如 趋势分析 V1',
    segment_cost INT           NOT NULL DEFAULT 50 COMMENT '消费金额（segments）',
    updated_by   BIGINT                 COMMENT '最后修改人 admin_id',
    updated_at   DATETIME               COMMENT '最后修改时间',
    PRIMARY KEY (service_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 服务消费定价表';

-- 初始化默认定价
INSERT IGNORE INTO ai_service_pricing (service_key, service_name, segment_cost) VALUES
    ('ai:trend:v1', '趋势分析 V1', 50),
    ('ai:trend:v2', '趋势分析 V2', 50),
    ('ai:plan',     '方案设计',   40),
    ('ai:match',    '岗位匹配',   50);

CREATE TABLE IF NOT EXISTS pricing_change_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    service_key VARCHAR(64)  NOT NULL COMMENT '服务标识',
    old_cost    INT          NOT NULL COMMENT '旧定价',
    new_cost    INT          NOT NULL COMMENT '新定价',
    admin_id    BIGINT       NOT NULL COMMENT '操作人 admin_id',
    changed_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
    PRIMARY KEY (id),
    KEY idx_service_key (service_key),
    KEY idx_changed_at (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定价变更审计日志表';

-- ============================================================
-- Open source contribution discovery and saved guides
-- ============================================================

CREATE TABLE IF NOT EXISTS open_source_repository (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(160) NOT NULL,
    display_name VARCHAR(160),
    organization_name VARCHAR(128),
    html_url VARCHAR(512) NOT NULL,
    stars INT NOT NULL DEFAULT 0,
    primary_language VARCHAR(64),
    tech_stack VARCHAR(512),
    node_version VARCHAR(64),
    package_manager VARCHAR(32),
    system_requirement VARCHAR(128),
    memory_requirement VARCHAR(128),
    disk_requirement VARCHAR(128),
    contribution_url VARCHAR(512),
    environment_notes VARCHAR(1000),
    tier VARCHAR(16) NOT NULL DEFAULT 'EXTENDED',
    priority INT NOT NULL DEFAULT 100,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    last_sync_status VARCHAR(16),
    last_sync_error VARCHAR(1000),
    last_sync_time DATETIME,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_open_source_repository_name (full_name),
    KEY idx_open_source_repository_enabled (enabled, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开源贡献仓库源';

CREATE TABLE IF NOT EXISTS open_source_issue (
    id BIGINT NOT NULL AUTO_INCREMENT,
    repository_id BIGINT NOT NULL,
    github_issue_id BIGINT NOT NULL,
    issue_number INT NOT NULL,
    title_original VARCHAR(512) NOT NULL,
    title_zh VARCHAR(512),
    summary_zh VARCHAR(1000),
    body_original MEDIUMTEXT,
    html_url VARCHAR(512) NOT NULL,
    labels_json JSON,
    state VARCHAR(16) NOT NULL DEFAULT 'open',
    comments_count INT NOT NULL DEFAULT 0,
    assignees_json JSON,
    linked_pr_count INT NOT NULL DEFAULT 0,
    claim_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
    participation_status VARCHAR(32) NOT NULL DEFAULT 'TRY',
    difficulty VARCHAR(16) NOT NULL DEFAULT 'EASY',
    risk_flags_json JSON,
    github_created_at DATETIME,
    github_updated_at DATETIME,
    verified_at DATETIME,
    translated_at DATETIME,
    translation_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    published TINYINT(1) NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_open_source_issue_github_id (github_issue_id),
    UNIQUE KEY uk_open_source_issue_repo_number (repository_id, issue_number),
    KEY idx_open_source_issue_public (published, state, participation_status, github_updated_at),
    KEY idx_open_source_issue_translation (translation_status, published),
    CONSTRAINT fk_open_source_issue_repository FOREIGN KEY (repository_id) REFERENCES open_source_repository(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开源贡献Issue缓存';

CREATE TABLE IF NOT EXISTS open_source_guide (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    issue_id BIGINT NOT NULL,
    content MEDIUMTEXT NOT NULL,
    segment_cost INT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_open_source_guide_account_issue (account_id, issue_id),
    KEY idx_open_source_guide_account_time (account_id, update_time),
    CONSTRAINT fk_open_source_guide_account FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT fk_open_source_guide_issue FOREIGN KEY (issue_id) REFERENCES open_source_issue(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户开源贡献指南';

INSERT IGNORE INTO ai_service_pricing (service_key, service_name, segment_cost) VALUES
    ('ai:open-source-match', '开源 Issue 智能匹配', 40),
    ('ai:open-source-job-match', '开源 Issue 岗位匹配', 40),
    ('ai:open-source-guide', '开源贡献指南', 50);
