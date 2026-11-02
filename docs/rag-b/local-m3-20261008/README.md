# B 版本地 BGE-M3 接入（2026-10-08）

## 范围与运行位置

Gitee 最后一次诊断仍在约 20 秒超时，因此按用户授权下载并接入本地模型。

- Ollama：官方 Windows 便携版 v0.40.0；程序位于 `D:\Orangejd\.local-models\ollama`。
- 下载包：`D:\Orangejd\.local-models\downloads`，已校验官方 SHA256。
- 模型缓存：`D:\Orangejd\.local-models\models`，含 blobs、manifests、manifests-v2 和 metadata。
- 模型：`bge-m3:orangejd-v1`，F16，1,157,672,605 字节，1024 维。
- 固定摘要：`sha256:7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`。
- 服务绑定 `127.0.0.1:11434`；Docker Desktop 实测能通过 `host.docker.internal:11434` 访问，无须开放公网监听。
- 本机 RTX 5070 Laptop 8 GiB 显卡已由 Ollama 检出并用于 CUDA 推理。

## Java 接入方式

复用当前 Spring AI 1.0.0 的 `AbstractEmbeddingModel`，新增 `RagBOllamaEmbeddingModel`，直接封装 Ollama HTTP 协议。没有额外引入 LangChain4j 或升级 Spring AI，也没有整体改动客服编排。

选择独立适配器，是为了沿用可中断的 Java HttpClient、客服整轮期限和取消语义，防止替换成普通同步客户端后丢失已有治理。查询上限仍为 12 秒（含排队），HTTP 上限 8 秒；批量入库独立上限 60 秒。没有隐藏重试，不会把 Gitee 密钥发送到本地服务。

每次模型调用前检查 `/api/tags` 的固定摘要。输出必须是按输入顺序返回的有限数值、1024 维且归一化的 dense 向量。设置 `truncate=false`，超长输入报错而不是静默丢弃；保持原结构分块策略。模型在服务运行期间保持加载，避免闲置后自动卸载引起冷启动超时；初次启动必须预热。

本地只输出 dense 向量，不启用 M3 sparse/ColBERT 新策略；Redis HNSW、关键词召回、融合权重、TopK、卡片和引用规则保持原实现。

## 索引隔离

V45 为 B 索引代际增加 `embedding_provider` 和 `embedding_revision`。历史代际默认 `GITEE`；旧 A 模型仍由原配置提供。运行配置的 provider 只决定新代际使用哪种模型，不能将已有 Gitee 代际静默切成 Ollama。

启动时 `AiConfig` 仍可能打印 `bge-small-zh-v1.5` 和 Gitee 地址，这是原 A 版的全局模型 Bean。B 的实际提供方以激活代际中的 `embeddingProvider=OLLAMA` 和固定摘要为准。

新代际独立生成向量，复用当前 READY 文档原文与结构分块。现有 `RagBGenerationService` 在完整写入、分块数量及文档快照一致性校验后进行事务切换，保留旧代际。新代际失败不会清空旧索引。

抽查 12 个旧片段，本地/云端余弦相似度最低约 0.9851，因此不将“模型名相同”视为向量完全兼容的证据。完整数据与结果以 `benchmark.json`、`integration.json` 为准。

旧代际的摘要统计为219文档／6227片段，但实际关联数据已经是565文档／8660片段。本次对同一批565份READY文档重新生成本地向量；不能将摘要栏的陈旧统计解释成此前只有219份资料或本次新增了346份资料。

## 启动与验证

日常启动在项目目录运行（也可双击根目录 `start-local.cmd`）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start_local.ps1
```

统一入口读取Compose解析后的配置，B启用且provider为OLLAMA时先启动模型、验证固定摘要与1024维归一化输出、完成预热，再启动前后端、数据库和Redis并等待就绪。模型或摘要校验失败时不继续启动项目；已运行的服务复用，不重新下载或建索引。默认等待600秒，可用 `-ReadyTimeoutSeconds` 修改；`-SkipWait` 仅跳过项目就绪等待，不跳过模型预热。直接执行 `docker compose up` 或IDE启动后端，不会自动启动Windows宿主机的模型进程；IDE可将模型脚本作为启动前任务。

只启动模型或排查时仍可运行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start_local_embedding.ps1 -Warmup
python scripts/local_m3_acceptance.py benchmark
```

模型已经下载，重启后无须重新拉取。启动脚本不注册开机任务，不修改系统 PATH。服务依赖本机处于开机状态；此接入属于本地开发环境，服务器发布需单独部署 Embedding 服务和配置。

重复验证已有索引时可运行以下命令，保存到独立目录，避免覆盖最初建库报告；`verify` 会先断言当前激活代际绑定了同一份本地模型，不创建或切换索引：

```powershell
python scripts/local_m3_acceptance.py verify --report-dir docs/rag-b/local-m3-20261008/startup-test
```

客服也需要在 `.env.local` 持久化启用、用户范围、模型、思考模式与执行器配置，不能只临时传给某次容器启动。此次启动复验发现缺失 `CUSTOMER_ENABLED` 后默认关闭客服，导致正常登录用户收到403；已恢复既定的全登录用户开放、deepseek-flash、思考/原生动作开启及LEGACY设置。统一入口在客服关闭时会提示，不把三个HTTP地址可达等同于客服业务可用。

仅在需要建立新代际时运行（会写入 B 文档版本与索引）：

```powershell
python scripts/local_m3_acceptance.py rebuild
python scripts/local_m3_acceptance.py start-backend
```

`rebuild` 不调用 DeepSeek 或云端 Embedding，只使用本机模型。`start-backend` 更新 `.env.local` 的 B 模型项并重建后端容器；密钥、客服模型与编排类型从原容器配置保留，不输出或写入报告。

配置涉及 `RAG_B_EMBEDDING_PROVIDER=OLLAMA`、固定 `RAG_B_EMBEDDING_REVISION`、本地模型标签、服务地址和批量大小 4。其他部署不得直接沿用宿主机地址，须配置实际可达的私有服务。

## 验证边界与成本

- `benchmark.json`：20 条合成查询、两并发、预热/延迟和 12 个旧片段向量对照；不代表持续容量测试。
- `integration.json`：完整 READY 文档重建、实际代际、8 次两并发检索与名称查回验证。
- `integration.log`：脱敏后的 Java 编译、真实 Flyway 与入库验收日志。
- `integration-execution.json`：进程退出状态；仅完整通过才可称接入验收成功。
- 单元测试覆盖查询/批量接口、维度、归一化、标签变更、截止时间、取消和 A/B 模型隔离。

本地 Embedding 没有供应商逐次调用费用；硬件、电力和运维成本没有测量。DeepSeek 回答生成费用不变。本次不重跑 80 轮客服模型评测，不据此宣称 LangGraph4j 已达到全量发布门槛。

## 回退注意

旧 A 实现与旧 B Redis 索引保留。因为 provider 绑定在代际上，仅把环境配置改回 GITEE 并不会改变已经激活的本地代际。恢复旧 B 时需要按原代际记录事务恢复 active 标记、文档版本与分块映射，并检查旧索引是否仍存在；不要仅替换 URL，也不要清空 Redis。

## 最终实施结果

- 激活代际：`local-m3-1791424349362`，ID=4，OLLAMA，565文档／8660片段；完整重建约354秒。原云端代际ID=3保留为RETIRED。
- 8次两并发完整检索全部返回8个片段，125–338ms；另通过真实名称来源查回。
- 客服、B库、架构回归：360项中340通过、20跳过，0失败／错误；跳过项不计作通过。真实本地入库验收另行启用并通过。
- 隔离MySQL验收：12项通过，空库与V40旧库均升级到V45；含连接池、事务和检查点规则。
- 后端已重建并运行，`/actuator/health` 返回UP。用户端15173、管理端15174均HTTP200。
- 账号5的确定性客服接口验收通过：会话成功、重复请求返回原运行、App令牌不能访问管理接口。原始报告为 `docs/customer/langgraph4j/local-m3-20261008-http-smoke.json`，本目录 `http-verification.json` 记录配置与索引关联；没有真实聊天模型调用。
- 客服引擎仍LEGACY，聊天模型仍deepseek-flash、思考与原生动作保持开启；A仍bge-small-zh-v1.5。
- 未重新执行80轮真实回答质量对照；本次没有验证管理员登录后的原文查看页面，不补造通过结论。
