# OrangeVC — AI 创意服务交易平台

> Vibe Coding 实践项目 · Spring Boot 3.3.5 + Vue 3 全栈 | ~88 API · 17 表 · 双前端 · RAG 知识库

## 在线地址

| 入口 | 地址 |
|------|------|
| 用户端 | http://47.116.8.45 |
| 管理后台 | http://47.116.8.45/admin |

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
└── deploy/             部署包（jar + 前端 dist + 数据库导出）
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

### 测试账号

| 身份 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | 123456 |
| 用户 | 自行注册（需邮箱验证码） | — |

## 生产部署

详细指南见 `docs/服务器部署完整指南.md`。

```bash
# 构建
cd D:/Orangejd
mvn clean install -DskipTests
cd orange-web && npm run build
cd orange-admin && npm run build

# 打包
cp -r orange-server/target/orange-server-1.0.0-SNAPSHOT.jar orange-web/dist/ orange-admin/dist/ deploy/
mysqldump -uroot -p123456 orangejd > deploy/orangejd_backup.sql

# 上传 + 部署（服务器）
\cp -f deploy/orange-server-1.0.0-SNAPSHOT.jar /root/
rm -rf /data/www/orangejd/web/* && cp -r deploy/web/* /data/www/orangejd/web/
rm -rf /data/www/orangejd/admin/* && cp -r deploy/admin/* /data/www/orangejd/admin/
mysql -uroot -p orangejd < deploy/orangejd_backup.sql
nohup /root/start.sh > /root/app.log 2>&1 &
```

### 关键配置

```bash
# start.sh 环境变量
export MAIL_USERNAME=你的QQ邮箱
export MAIL_PASSWORD=SMTP授权码
export AI_TREND_KEY=GiteeAIKey
export AI_EMBEDDING_KEY=GiteeAIKey
export TURNSTILE_SECRET_KEY=CloudflareKey
export JWT_ADMIN_SECRET=$(openssl rand -base64 48)
export JWT_APP_SECRET=$(openssl rand -base64 48)
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
| 服务器部署完整指南 | `docs/服务器部署完整指南.md` |
| 系统架构设计说明书 | `docs/OrangeJD系统架构设计说明书.md` |
| 项目进度记录 | `docs/项目进度记录.md` |
| 开发日志（最新） | `docs/开发日志/2026-07-09-多模块增强.md` |
| 前端 UI 设计规范 | `docs/OrangeJD前端UI设计规范.md` |
| 全量表设计 | `orange-server/src/main/resources/sql/schema-v1.sql` |

## License

MIT
