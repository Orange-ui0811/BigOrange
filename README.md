# OrangeVC — AI 创意服务交易平台

> Vibe Coding 实践项目 · Spring Boot 3.3.5 + Vue 3 全栈 | ~88 API · 17 表 · 双前端 · RAG 知识库

## 在线地址

| 入口 | 地址 |
|------|------|
| 用户端 | 由生产环境 HTTPS 域名配置 |
| 管理后台 | 由生产环境 HTTPS 域名的 `/admin/` 路径提供 |

## 技术栈

| 层 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.3.5 |
| JDK | Java 21 |
| ORM | MyBatis 3.0.3 |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis Stack（Docker，含 RediSearch 模块） |
| AI 框架 | Spring AI 1.0.0（VectorStore + RAG） |
| LLM | DeepSeek V4 Pro（Gitee AI） |
| Embedding | bge-small-zh-v1.5（512 维，中文原生） |
| 实时通信 | WebSocket（消息 + 通知实时推送） |
| 前端 | Vue 3.5 + Element Plus 2.14 + Vite 8.0 |
| 安全 | JWT 双通道鉴权 + BCrypt + Turnstile 人机验证 |
| 邮件 | QQ 邮箱 SMTP |
| 部署 | Maven Fat JAR + Nginx 反向代理 + Docker Redis Stack |

## 项目结构

```
OrangeJD
├── orange-common/     共享工具：Result、PageResult、JwtUtil、BaseContext、异常类
├── orange-pojo/       数据模型：Entity、DTO、VO
├── orange-server/     后端服务：Controller、Service、Mapper、Task、Config、AI
├── orange-web/         用户端前端（Vue 3 + Element Plus）
├── orange-admin/      管理后台前端（Vue 3 + Element Plus）
├── docs/               项目文档 + 开发日志 + 部署指南
└── scripts/            构建、公开导出与服务器服务模板
```

## 功能模块

### 核心业务

| 阶段 | 内容 | API 数 |
|------|------|--------|
| 账号体系 | 注册/登录/改密/忘记密码，邮箱验证码 + BCrypt + Turnstile | 8 |
| 分类管理 | 15 个 Topic 分类（中英对照，含 topicName） | 5 |
| 内容发布 | 作品/需求发布 + 审核状态机 + 编辑保存 | 22 |
| 内容发现 | 市场浏览 + 收藏 + 评论 + 浏览历史 + 作者主页 | 15 |
| 沟通协商 | 会话 + 消息 + 通知 + WebSocket 实时推送 | 10 |
| 交易闭环 | 报价 + 订单 + 支付阶段 + 交付验收 | 15 |
| 管理端 | 用户管理 + 订单管理 + 统计仪表盘 | 8 |
| 定价管理 | AI 服务定价 MySQL 存储 + 变更审计日志 | 2 |

### AI 增强

| 功能 | 说明 |
|------|------|
| RAG 知识库 | Spring AI VectorStore + 中文翻译 README + 自动采集 + 向量检索 |
| 趋势分析 | 四维画像 + DeepSeek 个人化推荐 + 自由提问 |
| GitHub 榜单 | 15 Topic × 3 时段（全时段/本周涨星/本周新增），周一自动爬取 + 中文翻译 |
| Topic 知识图谱 | ECharts 力导向图 + 14 节点热度分析 |
| 方案设计 | AI 六阶段项目规划（想法→需求→标准→风险→任务→岗位匹配） |
| 工作能力参考 | 方向/岗位浏览 + Markdown 详情 + 投稿 |
| AI 自动审核 | DeepSeek 多模态审核 + 飞书通知 |
| 富文本编辑 | 详情 HTML 渲染 + 图片上传 |

### 其他功能

| 功能 | 说明 |
|------|------|
| 交易模式开关 | 管理端一键切换免费/交易模式 |
| 邮箱绑定 | 一邮箱一号，侵权追溯 + 唯一索引 |
| 忘记密码 | 邮箱验证码重置 |
| Turnstile | Cloudflare 人机验证注册防护 |
| 🍊 Segments | 配额消费 + 充值码兑换 + 在线支付 |
| 使用指南 | 侧边栏像素橙子气泡，6 表情 × 23 路由自适应 |

## 快速开始

### 环境要求

- JDK 21+
- MySQL 8.0
- Redis Stack（Docker，端口 6380/6379）
- Maven 3.x
- Node.js 18+

### 本地启动

推荐使用 Docker Compose（无需在宿主机安装 JDK、Maven、MySQL 或 Redis）：

```bash
cp .env.local.example .env.local
docker compose --env-file .env.local -f compose.local.yml up -d
```

本地 Compose 使用独立端口，避免与其他开发实例冲突：用户端
`http://localhost:15173`、管理端 `http://localhost:15174/admin/`、后端及
Swagger `http://localhost:18080/swagger-ui/index.html`。停止服务使用：

```bash
docker compose --env-file .env.local -f compose.local.yml down
```

需要邮件、AI、GitHub 或 Turnstile 功能时，在 `.env.local` 中填入对应密钥；
不配置时不影响核心页面、数据库与登录功能启动。

手动启动方式：

```bash
# 1. Redis Stack
docker start redis-stack || docker run -d --name redis-stack -p 6380:6379 -p 8002:8001 redis/redis-stack:latest

# 2. 数据库
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS orangejd"
mysql -uroot -p123456 orangejd < orange-server/src/main/resources/sql/schema-v1.sql

# 3. 后端
cd OrangeJD
mvn install -DskipTests
cd orange-server
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 4. 用户端
cd orange-web && npx vite --host

# 5. 管理后台
cd orange-admin && npx vite --host
```

### 访问地址

| 服务 | 地址 |
|------|------|
| 后端 API | http://localhost:8080 |
| Swagger 文档 | http://localhost:8080/swagger-ui/index.html |
| 用户端 | http://localhost:5173 |
| 管理后台 | http://localhost:5174/admin/ |

### 本地开发测试账号

| 身份 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | 123456 |
| 用户 | 自行注册（需邮箱验证码） | — |

本地 `dev` 环境不要求配置 SMTP。点击“获取验证码”后，在后端日志中查找
`[仅本地开发] 邮箱验证码`；生产环境仍使用真实邮件发送，不会输出明文验证码。

该管理员口令仅用于隔离的本地开发数据库；生产环境不得使用或公开默认口令。

## 生产部署

从 GitHub 克隆、初始化数据库、构建发布包和回滚的完整指南见
`docs/GitHub克隆与服务器数据库部署.md`；Cloudflare 与 HTTPS 见
`docs/Cloudflare域名与HTTPS接入手册.md`；支付门禁见
`docs/ePay订单支付接入与服务器交接手册.md`。生产变量从 `.env.prod.example`
复制到服务器密钥文件，不要把真实值写回仓库。

```bash
# 发布前验证与构建
VITE_TURNSTILE_SITE_KEY='PUBLIC_SITE_KEY' \
VITE_LEGAL_OPERATOR='运营主体全称' \
VITE_LEGAL_CONTACT_EMAIL='网站公示的联系邮箱' \
./scripts/build-release.sh

# 发布前备份（密码通过环境变量或密钥管理系统注入）
MYSQL_PASSWORD='从密钥管理系统读取' BACKUP_DIR=/data/backups/orangejd ./backup.sh

# 后端首次启动会由 Flyway 自动校验并迁移数据库
APP_JAR=/data/orangejd/orange-server-1.0.0-SNAPSHOT.jar ./start.sh
```

### 关键配置

```bash
# 必填变量；完整清单见 .env.prod.example
export MYSQL_PASSWORD='随机数据库密码'
export REDIS_PASSWORD='随机 Redis 密码'
export JWT_ADMIN_SECRET=$(openssl rand -base64 48)
export JWT_APP_SECRET=$(openssl rand -base64 48)
export ALLOWED_ORIGINS='https://example.com,https://admin.example.com'
export TURNSTILE_SECRET_KEY='CloudflareKey'
```

### Nginx 配置要点

```nginx
location / { try_files $uri /index.html; }
location /api/ { proxy_pass http://127.0.0.1:8080/; }
location /ws/ { proxy_pass http://127.0.0.1:8080; proxy_http_version 1.1; proxy_set_header Upgrade $http_upgrade; proxy_set_header Connection "upgrade"; }
location ^~ /admin { alias /data/www/orangejd/admin; try_files $uri /admin/index.html; }
location ^~ /uploads/ { alias /root/uploads/; }  # 必须 ^~ 覆盖图片正则
```

## AI 功能配置

### AI 自动审核

管理端开启「AI 托管」→ 新提交自动审核（DeepSeek V4 Pro 多模态）→ 结果飞书通知。

### AI 趋势分析

四维画像表单 + Topic 全景图 + RAG 知识库检索增强 + AI 个人化推荐。

### GitHub 榜单爬取

每周一凌晨 2:00 自动爬取 15 个 Topic × 3 时段，爬完后统一逐条翻译为中文，失败死磕到成功。

### 换 AI 模型

修改 `start.sh` 中 `AI_TREND_MODEL` / `AI_AUDIT_MODEL` / `AI_EMBEDDING_MODEL` 环境变量，重启即生效。

## 项目文档

| 文档 | 路径 |
|------|------|
| GitHub 克隆与生产部署 | `docs/GitHub克隆与服务器数据库部署.md` |
| Cloudflare 域名与 HTTPS | `docs/Cloudflare域名与HTTPS接入手册.md` |
| ePay 接入与服务器交接 | `docs/ePay订单支付接入与服务器交接手册.md` |
| 系统架构设计说明书 | `docs/OrangeJD系统架构设计说明书.md` |
| 项目进度记录 | `docs/项目进度记录.md` |
| 开发日志（最新） | `docs/开发日志/2026-07-09-多模块增强.md` |
| 前端 UI 设计规范 | `docs/OrangeJD前端UI设计规范.md` |
| 全量表设计 | `orange-server/src/main/resources/sql/schema-v1.sql` |

## License

MIT
