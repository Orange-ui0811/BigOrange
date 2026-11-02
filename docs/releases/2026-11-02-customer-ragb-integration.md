# Customer Agent 与 RAG-B 精简集成

## 范围

从 main `178628f7` 创建 `integration/customer-ragb-20261102`，将快照 `3a26d9e2` 相对原 V3 `68913b58` 的功能差异应用到最新 main。保留已合并的覆盖率测试、Vue 安全更新、锁文件、Gitleaks 权限及精确历史误报豁免。

包含客服任务、需求与追问、LangGraph4j、上下文摘要与历史回查、受控工具、B 版入库/混合检索/Reranker、管理端诊断、共享项目卡片，以及 V38–V46 迁移。README 知识过滤和知识单元准备代码保留，但仍为实验准备链路；没有替换线上 B 索引。

未导入完整实验文档、模型输出归档、运行数据、真实聊天记录、数据库备份、模型权重或本地密钥。29 个依赖本机历史实验目录的整类回放/云端验收测试留在原快照分支。普通单元测试、必要的小型固定样本和数据库测试保留。17 MB 的 `rag-b/tokenizers/bge-m3.json` 被 `RagBTokenChunker` 直接加载，是运行时分块资源，不是实验报告。

为满足既有代码体积预算，将 README 结构解析、依赖图、选择提示词、Redis 元数据字段和客服快照/用量/证据预算分别拆出辅助类，未放宽预算。补回共享推荐卡片的引用链接样式类。修正数据库测试仍断言 V45 的旧值。

## 默认开关

Java 默认值、application.yml、Compose 和两份配置示例均使用：

```dotenv
CUSTOMER_ENABLED=false
RAG_B_ENABLED=false
RAG_B_RERANK_ENABLED=false
CUSTOMER_ALL_AUTHENTICATED_USERS=false
```

白名单通过 `CUSTOMER_ALLOWED_ACCOUNTS` 配置；执行引擎默认 LEGACY，验证图引擎时显式指定 `CUSTOMER_EXECUTION_ENGINE=LANGGRAPH4J`。审计原文入口默认关闭。关闭功能开关不能回滚数据库迁移。

## 本地验收

- Java 21 完整 `mvn -B clean verify`，实际启用隔离 MySQL 的会话、图持久化和上下文测试；随后启用独立 Redis Stack，补跑真实 HNSW/正文检索测试。
- 合并两次报告后：1058 项中 1053 通过、0 失败、0 错误、5 项条件未启用。五项分别是历史用途语料探针、依赖导出、知识块预览、投影导出和真实模型 RAG smoke；没有把它们记作通过。
- 图持久化测试实际执行 220 次模拟模型对话，包含两种引擎、并发 2、检查点和连接释放；不据此宣称真实模型端到端性能。
- 用户端：164 项单测、lint、类型检查、覆盖率、生产构建和生产依赖审计通过；stores 分支覆盖率 81.72%，行 99.08%。
- 管理端：5 项单测、lint、类型检查、生产构建和生产依赖审计通过。两端生产依赖审计均为 0 漏洞。
- Playwright：18 项通过；1 项需要真实测试身份的云端验收未启用。包括原业务、客服界面、管理诊断和趋势推荐回归，API 使用构造替身。
- 架构体积和前端 JS 分块预算通过。
- 独立运行后端端口 18880：关闭功能时 5 项 HTTP 检查通过，白名单及 LangGraph4j 模式 13 项检查通过，包含鉴权、重复请求复用、模型调用不重复、SSE 重连、会话历史。模型只访问本地固定替身 18999，未调用云端模型。跨账号细分隔离还由真实 MySQL 测试覆盖。

上述 HTTP 脚本只面向新建的隔离库和构造账号 900001/900002，不可指向现有用户数据库。

## 数据库恢复与升级

本轮仅操作独立 MySQL 容器 `bigorange-v3-merge-mysql`（127.0.0.1:18336）；Redis 测试使用 `bigorange-integration-redis`（127.0.0.1:18379）。原应用数据库没有执行迁移或写入。

1. 构造数据：建立 V37 库，实际 mysqldump 备份、恢复到另一空库，再升级 V46；同时验证 V1–V46 空库迁移、validate、重复迁移无变更及额度数据保留。
2. 历史真实本地备份：找到 `D:\Orangejd\backups\local-safe-update-20260808\orangejd-before-v37.sql`，文件 SHA-256 为 `9f8a433f736cf4656abe8dfadf5b151b07f195aeb716ea6db64de0bd042ed2b8`。实际迁移版本是 V36，不冒充原生 V37 备份。
3. 将该备份恢复到隔离库，先执行 V36→V37，再执行 V37→V46；Flyway 校验和重复迁移通过。升级前后按原有全部列比较 14 个账号、38 条额度流水、680 个知识文档的行数和内容摘要，一致。新增列不参与原列比较。
4. 只读检查当前工作库，已处于 V46；无需对它重复“升级”。历史数据备份始终保留在本地忽略路径，未加入 Git。

这是本地备份恢复演练，不代表生产数据库已经备份或升级。生产部署仍需重新备份目标数据库，核对其真实版本与配置。

## 持续检查与后续

CI 保留原有门槛和安全扫描，新增独立 Redis Stack，并强制真实 Redis 测试在服务不可用时失败。数据库任务新增真实 MySQL 会话、图持久化和上下文测试，避免将基础设施缺失的 skip 当作核心验收通过。

本轮创建 Draft PR，远端检查结果以最新提交的 checks 为准。后续审查通过后再合并；本轮不部署生产、不切换知识库索引、不自动开放客服或 Reranker。

复现工具：`scripts/ci/CustomerRagMigrationSmoke.java`、`ExistingBackupMigrationSmoke.java`、`customer_http_smoke.py`、`customer_model_stub.py`。迁移脚本面向新建的固定测试库；已有同名库时不自动清库。Java 源文件模式运行需要 orange-server 的 Maven 依赖 classpath。HTTP 验收使用隔离服务器的公开开发 JWT 默认值，禁止连接业务环境。
