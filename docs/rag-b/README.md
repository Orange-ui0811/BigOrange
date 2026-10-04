# B 版 RAG 实验模块

这个模块以 Spring AI Alibaba Examples 的组件边界为起点，在 OrangeJD 内建立独立实验链路。它复用平台的 MySQL、Redis、EmbeddingModel 和后台任务系统，但不修改旧知识库表、旧 Redis 向量键或用户侧 RAG 路由。

## 当前可验证能力

- B 版数据只写入 `rag_b_*` 表和 `orange:rag:b:` Redis 命名空间。
- 模块默认关闭；`RAG_B_ENABLED=true` 后才能上传、导入、检索或重建。
- 手动上传、从原知识库按文档 ID 导入、直接同步 GitHub README 走同一套版本、分块、元数据和向量流程。
- 文档状态区分等待处理、处理中、可检索、失败和已下架；失败版本可以重试。
- Markdown 按标题和正文边界切分，记录章节路径和原文字符区间；达到上限时整篇失败，不静默丢弃尾部。
- 内容哈希相同且最新版本已经发布时跳过；新版本失败时继续使用上一版。
- GitHub README 使用平台已有 GitHub 客户端的令牌、限流、超时和重试策略；同步完整 README，不截取前 4,000 字符，再次同步通过内容哈希增量更新。
- GitHub README 支持每日定时增量同步；默认关闭，可限制每轮仓库数，单个仓库失败不会中断整轮任务，并通过进程内互斥避免任务重叠。
- 明确的语言、周期、难度和运行依赖可以作为硬条件。缺少字段会按“未知”排除，不会猜测满足。
- 岗位、入门项目和职业项目在返回前重新检查业务表状态，已下架或未审核资料不会作为有效证据。
- 删除先写下架状态，再清理所有 B 版索引代际中的向量键；清理失败也不会继续被检索。
- 索引重建写入新代际，核对文档快照和片段总数后再原子切换；失败时保留当前代际。
- 每个索引代际固定记录检索适配器。`EXACT` 保留为可核验基线；`HNSW_HYBRID` 使用 Spring AI RedisVectorStore 的 HNSW 索引，并把向量、正文全文以及标题/来源标识召回通过加权 RRF 融合。正文关键词权重为 1.15、向量权重为 1.0、名称通道权重为 3.0；规范化后的完整项目名命中另加确定性分，解决点名项目被通用语义片段挤出的问题。
- 检索返回向量排名、关键词排名、硬条件排除原因和偏好加权路径；“新手优先”和“求职优先”只参与硬条件过滤后的排序，不能绕过下架与审核状态。
- 最终截断先覆盖不同项目或来源，再用剩余名额补充同一来源的其他章节；同一项目或来源在证据组中最多聚合三个相关章节。少量单一证据会进入 `INSUFFICIENT_EVIDENCE`，Spring AI Retriever 此时不向生成模型提供上下文。
- 回答预览只在证据充分时调用平台现有文本模型。模型被要求逐个事实句添加 `[C数字]`，服务端随后校验引用是否存在、是否指向本次证据以及句子与证据是否有文本支持；未通过的回答标记为不可发布。
- 管理端 `/#/rag-lab` 提供上传、旧库导入、状态、重试、下架、试搜、过滤原因、证据位置、索引代际、适配器选择、回答引用校验、基础 A/B 检索对比和固定评测。
- `RagBDocumentRetriever` 实现 Spring AI 1.0.0 的 `DocumentRetriever`，后续可以直接装配 `RetrievalAugmentationAdvisor`、`DocumentJoiner` 和 `DocumentPostProcessor`。
- 固定评测集当前为 `rag-b-quality-v2`，包含 12 个覆盖入门项目、职业项目、岗位、README、硬条件、追溯和证据不足的用例。每轮同时验证来源、硬条件和回答/拒答策略，并保存 A/B 通过率、B 版可追溯率、引用就绪率、拒答准确率以及两侧 P50/P95。

## 本地启用

1. 启动平台已有的 MySQL 和 Redis Stack。
2. 为后端设置 `RAG_B_ENABLED=true`。Embedding 配置继续使用平台现有的 `AI_EMBEDDING_*` 环境变量。
3. 启动后端。Flyway 会创建 `rag_b_*` 表，不会改写旧知识文档。
4. 进入管理端“RAG 实验室”，可以上传测试文档、输入旧知识库文档 ID，或输入 `owner/repository` 同步完整 GitHub README。
5. 等文档状态变成“可检索”后执行试搜；需要性能索引时选择“HNSW 混合检索”重建新代际，需要可核验基础线时选择“精确余弦基线”。
6. HNSW 模式要求现有 Redis 服务包含 RediSearch 和 RedisJSON，也就是平台原知识库使用的 Redis Stack；不增加新的 Redis 实例。

可选的 README 自动同步配置：

```dotenv
RAG_B_GITHUB_SYNC_ENABLED=true
RAG_B_GITHUB_SYNC_LIMIT=50
RAG_B_GITHUB_SYNC_CRON=0 15 4 * * *
```

默认时区沿用平台的 `Asia/Shanghai`。Cron 默认为每天 04:15，每轮只扫描最近的指定数量 GitHub 文档。

关闭 `RAG_B_ENABLED` 后，实验数据仍然保留，但所有写入和检索操作会被拒绝。旧知识库和用户入口不依赖这个开关。

## 当前实现边界

`EXACT` 把向量存入独立 Hash，并以精确余弦计算，设置候选安全上限。`HNSW_HYBRID` 使用独立 RedisJSON 前缀和独立 RediSearch 索引；两者共享平台 Redis 连接、EmbeddingModel、MySQL 和任务队列，但不会读写 A 版向量键。

当前证据充分性使用可解释的保守规则：至少两个独立章节，或累计有效证据达到 500 字符。它可以阻止明显缺证据的生成，但仍需用人工标注的业务问题持续校准。逐句引用校验是确定性文本重合校验，适合作为发布闸门的第一层，不能替代语义蕴含模型或人工事实审查。

## 已完成的工程验收

- RAG B 单元与集成测试共 25 个：24 个通过，真实 Redis Stack 测试在独立测试端口未启动时按设计跳过；运行中的真实 Redis Stack 已由 60 题基准覆盖。
- 真实 Redis Stack 集成测试写入 100 个片段，并发执行 40 次混合检索；本轮本机测试 P95 为 11 ms（重复运行观测为 11–18 ms），验收上限为 5 秒。
- 测试覆盖完整分块、元数据、状态流转、索引代际、GitHub 增量同步、检索策略、Spring AI Retriever、引用校验和真实 HNSW/全文融合。
- 管理端生产构建通过。

## 替换 A 版之前仍需满足的边界

1. 把 12 个冒烟用例扩展为至少 60 个由业务人员确认相关文档或片段的金标问题，再计算 Recall@K、nDCG@K、硬条件满足率和逐句引用支持率。当前“通过率”主要检查期望来源类型与空结果策略，不足以单独决定迁移。
2. 使用生产候选的真实 Embedding 和文本模型跑完整评测，记录 Token、费用、限流与超时。当前 Redis 集成测试使用确定性的本地假向量，只验证索引、融合和并发路径。
3. 在生产等量数据副本上执行索引重建、进程重启、Redis 短暂不可用、GitHub 限流和删除补偿演练，并确认旧代际持续可用。
4. 连续观察一段双跑周期，确认 B 版在质量门槛、P95、错误率和成本上均达到约定值，再把少量用户入口灰度切到 B 版。

只有评测与工程门槛全部通过后才讨论迁移。这个模块不会自动替换 A 版。

第一轮 60 份样本的实测结论、失败用例和成本边界见 [first-round-report.md](first-round-report.md)，原始结果见 [first-round-result.json](first-round-result.json)。

完成条件修正和补齐 8 份代表性资料后的第二轮结果见 [second-round-report.md](second-round-report.md)，热态机器结果见 [second-round-result.json](second-round-result.json)，冷态结果见 [second-round-cold-result.json](second-round-cold-result.json)。

60 题来源金标基准在排序修正后达到 B 版严格通过率和 Recall@8 均为 100%，结果、原失败根因及修正方式见 [benchmark-v1-report.md](benchmark-v1-report.md)，原始结果见 [benchmark-v1-result.json](benchmark-v1-result.json)，原失败题修正后的排名见 [benchmark-v1-failure-diagnostics.json](benchmark-v1-failure-diagnostics.json)。
