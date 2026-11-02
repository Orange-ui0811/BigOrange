# OrangeJD 多模型调用池与全局 80 并发改进计划

## 1. 项目目标

在 OrangeJD 现有代码基础上建立统一的交互式 AI 工具调用池，实现：

1. 使用 10 个 Gitee AI Key 作为主调用池。
2. 每个 Gitee Key 同一时刻最多处理 1 个请求。
3. 10 个 Gitee Key 全部占用时，自动使用 DeepSeek V4 Pro。
4. 全平台交互式 AI 工具最大同时运行数为 80。
5. Gitee 当前运行多少个请求，DeepSeek 就动态补足剩余容量。
6. 管理端统一管理 10 个 Gitee Key 和 1 个 DeepSeek 兜底 Key。
7. 管理端实时显示供应商、Key 编号、任务、用户、状态和耗时。
8. 文本与翻译、AI 托管审核、岗位 AI 审核、Embedding 保持独立。
9. 保持当前用户配额扣减和失败退款语义。
10. 通过灰度和全链路压测后，再正式开放 80 并发。

容量关系：

```text
全局最大运行数 = 80
Gitee 最大运行数 = 10
每个 Gitee Key 最大运行数 = 1
DeepSeek 可用数 = 80 - 当前 Gitee 运行数
```

| Gitee 运行数 | DeepSeek 最多运行数 | 总运行上限 |
|---:|---:|---:|
| 0 | 80 | 80 |
| 3 | 77 | 80 |
| 10 | 70 | 80 |

## 2. 用户提供的材料

用户只提供：

```text
C:\Users\l\Desktop\.env.local
```

实施时从该文件读取所需配置，但：

- 不复制到项目；
- 不提交 Git；
- 不输出完整 Key；
- 不读取与任务无关的密钥；
- 日志和报告只使用 Key 编号和不可逆指纹；
- 重复变量以文件中最后一次定义为准。

需要读取的变量：

```env
GITEE_AI_ENDPOINT=https://api.moark.com/v1
GITEE_AI_MODEL=DeepSeek-V4-Pro
GITEE_AI_KEY_1=
GITEE_AI_KEY_2=
GITEE_AI_KEY_3=
GITEE_AI_KEY_4=
GITEE_AI_KEY_5=
GITEE_AI_KEY_6=
GITEE_AI_KEY_7=
GITEE_AI_KEY_8=
GITEE_AI_KEY_9=
GITEE_AI_KEY_10=

AI_TREND_ENDPOINT=https://api.deepseek.com
AI_TREND_KEY=
AI_TREND_MODEL=deepseek-v4-pro

AI_AUDIT_ENDPOINT=
AI_AUDIT_KEY=
AI_AUDIT_MODEL=

AI_EMBEDDING_ENDPOINT=
AI_EMBEDDING_KEY=
AI_EMBEDDING_MODEL=

AI_CONFIG_ENCRYPTION_KEY=
```

如果暂时没有 `AI_TEXT_*`，改造时新增：

```env
AI_TEXT_ENDPOINT=
AI_TEXT_KEY=
AI_TEXT_MODEL=
```

其值从改造前文本与翻译实际使用的配置迁移，不要求用户额外整理。

## 3. 自动从项目提取的材料

实施过程从现有项目自动提取：

- AI 接口和调用链；
- 真实系统提示词和用户输入结构；
- 方案生成、趋势分析、上线体检、岗位匹配和开源指南请求体；
- 配额价格 Key、扣费和退款逻辑；
- 前后端超时设置；
- Redis 和 Nginx 限流；
- 管理端动态配置结构；
- Agent 设备任务结构；
- `system_task` 执行机制。

测试数据使用现有提示词结构生成脱敏样本，不使用真实用户隐私数据。

## 4. 当前项目基线

### 4.1 当前调用模式

| 功能 | 调用模式 |
|---|---|
| 趋势分析 V1/V2 | 用户请求线程同步调用 |
| 方案生成 | 用户请求线程同步调用 |
| 上线体检 | 用户请求线程同步调用 |
| 岗位匹配 | 用户请求线程同步调用 |
| 开源匹配与指南 | 用户请求线程同步调用 |
| Agent 设备任务 | 独立固定 2 线程 |
| 普通 AI 审核 | 独立固定 2 线程 |
| 岗位 AI 审核 | `system_task` 后台任务 |
| Embedding 重建 | 独立单线程 |

第一阶段不强制把所有同步接口改成异步。

### 4.2 当前超时

| 功能 | 当前服务端超时 |
|---|---:|
| 趋势分析模型请求 | 未显式设置读取超时 |
| 方案生成 | 连接 10 秒、读取 240 秒 |
| 上线体检 | 连接 10 秒、读取 120 秒 |
| 岗位匹配 | 连接 10 秒、读取 110 秒 |
| `ExternalAiClient` | 连接 10 秒、读取 120 秒 |
| 岗位 AI 审核 | 连接 10 秒、读取 90 秒 |
| Nginx | 连接 5 秒、读取 300 秒 |

前端 Axios 默认 15 秒，但部分 AI 页面覆盖为 90、120、150、240 秒或不限时。第一阶段不设置统一读取超时，必须逐接口核对前后端边界。

### 4.3 当前执行器

```text
通用后台执行器：核心 2、最大 4、队列 100
AI 审核执行器：固定 2、队列 500
Agent 设备执行器：固定 2
Embedding 重建：单线程
```

通用后台执行器不能直接承担 80 个交互式 AI 任务。

### 4.4 当前配额语义

```text
生成 requestId
→ 扣费
→ 调用模型
→ 成功返回
→ 失败使用原 requestId 退款
```

现有退款使用 `requestId:refund` 保证幂等，本次继续沿用。

## 5. 改造范围

### 5.1 纳入统一 AI 工具池

- 趋势分析 V1/V2；
- 用户画像趋势分析；
- 项目方案生成；
- 上线体检；
- 岗位匹配；
- 开源 Issue 匹配；
- 开源贡献指南；
- Agent 触发的上述功能。

### 5.2 保持独立

- 文档文本生成与翻译；
- README 和排行榜翻译；
- AI 托管审核；
- 岗位 AI 审核；
- RAG Embedding 和知识库重建；
- GitHub 抓取。

### 5.3 第一阶段暂不改造

- 统一 AI 持久化任务队列；
- 所有用户 AI 接口异步化；
- 用户排队位置；
- 自动成本路由；
- 通用自动重试和熔断；
- 多应用实例扩容。

## 6. 配置职责重新划分

新增内部配置类型：

```text
TEXT
TOOL_POOL
TOOL_FALLBACK
```

| 配置 | 用途 |
|---|---|
| `TEXT` | 文档、README、排行榜翻译 |
| `TOOL_POOL` | 10 个 Gitee Key |
| `TOOL_FALLBACK` | DeepSeek 兜底 |
| `AUDIT` | AI 审核 |
| `EMBEDDING` | RAG 向量化 |

旧的 `TREND/PROJECT/CONTRIBUTION` 暂时保留解析能力，但不再用于新交互式任务。

环境变量映射：

```text
GITEE_AI_*       → TOOL_POOL
AI_TREND_*       → TOOL_FALLBACK
AI_TEXT_*        → TEXT
AI_AUDIT_*       → AUDIT
AI_EMBEDDING_*   → EMBEDDING
```

管理端加密动态配置优先；不存在时使用环境变量。服务启动不得覆盖管理端已保存版本。

## 7. 先拆分文本与翻译配置

当前文档翻译存在 `DocumentServiceImpl.translateReadme() → ExternalAiClient.callTrend()` 等复用。如果直接改变 `AI_TREND_*` 职责，翻译功能会被意外切换。

正确顺序：

1. 在 `AiRuntimeConfigService` 新增 `TEXT`；
2. 在 `ExternalAiClient` 新增 `callText(...)`；
3. 修改 `DocumentServiceImpl`、`LeaderboardRefreshTask`、`OpenSourceTranslationService` 及其他翻译调用；
4. 验证翻译模型、输出、失败处理和旧任务配置版本；
5. 完成后才将 `AI_TREND_*` 定义为 DeepSeek 兜底。

## 8. 统一 AI 工具路由

建议新增：

```text
AiToolRouter
AiToolPurpose
AiToolRequest
AiToolResponse
AiToolPoolConfigService
AiToolSlotLeaseService
AiToolProviderClient
AiToolCallRegistry
AiToolCallLogService
```

调用路由器时传入：

```text
purpose
requestId
accountId
businessType
businessId
模型请求体
业务超时策略
```

路由器负责供应商选择、HTTP 调用、槽位控制和调用记录，不负责用户扣费、退款或业务结果解析。

## 9. 路由与并发规则

```text
检查全局 80 并发许可
        ↓
查询 AVAILABLE 的 Gitee 槽位
        ↓
按最久未使用排序并原子获取
        ↓
成功：调用 Gitee
失败：尝试下一槽位
        ↓
10 个槽位均不可用
        ↓
调用 DeepSeek
```

必须满足：

```text
Gitee 运行数 + DeepSeek 运行数 ≤ 80
```

超过 80 时，第一阶段在扣费前短暂等待；仍无许可则返回“AI 服务繁忙，请稍后重试”，不扣用户额度。第二阶段再实现持久化排队。

## 10. Gitee Key 槽位控制

10 个槽位：

```text
GITEE-01 ... GITEE-10
```

状态：

```text
AVAILABLE
BUSY
THROTTLED
INVALID
DISABLED
```

Redis 键：

```text
orangevc:ai-tool:slot:1
...
orangevc:ai-tool:slot:10
```

租约只保存 `leaseId/requestId/accountId/purpose/businessId/startedAt/instanceId`，不保存完整 Key。

租约要求：

- 原子获取；
- 同一槽位只能被一个请求占用；
- 调用期间续租；
- 释放时校验 `leaseId`；
- 正常完成主动释放；
- 进程崩溃后自动过期；
- 不使用固定 120 秒覆盖所有业务。

## 11. 全局 80 并发控制

Redis 维护全局运行许可，保证单实例和多实例都不超过 80。获取业务槽位失败、请求结束、异常和超时时必须可靠归还许可。

第一阶段仍为同步调用，80 个长请求可能占用约 80 个 Servlet 线程。验收必须同时观察：

- Tomcat 活动线程；
- 普通接口延迟；
- 数据库和 Redis 连接；
- JVM 内存和 CPU；
- 网络连接数。

如果普通业务受到明显影响，第二阶段必须引入独立 AI 执行器或异步任务。

## 12. 超时、重试和错误策略

### 12.1 超时

第一阶段保留各业务当前边界，不设置统一读取超时。趋势分析单独核对前端 15/90 秒和不限时的差异；方案生成保留 240 秒，上线体检 120 秒，岗位匹配 110 秒，审核保持独立 90 秒，Nginx 保持 300 秒。

读取超时表示结果未知：

- 记录 `TIMEOUT`；
- 不自动再次调用；
- 不直接判定 Key 失效；
- 按现有业务失败流程退款；
- 通过租约机制安全释放。

### 12.2 重试

第一阶段不设置通用“重试 1 次”。

仅在以下情况允许重新路由：

- HTTP 请求尚未发出；
- 连接建立前失败；
- Gitee 明确返回 429 并拒绝执行；
- Gitee 明确返回无可用渠道。

读取超时、部分响应、解析失败、模型业务格式不合格和执行状态不明时不自动重试。

### 12.3 错误状态

- 401/403：槽位标记 `INVALID`，停止分配；
- 429：有 `Retry-After` 时按其标记 `THROTTLED`，不写死 30 秒；
- 503：区分无可用渠道、模型不存在、网关异常和推理资源不足；
- 读取超时：记录但不认定 Key 永久异常。

第一阶段不采用统一“连续失败 3 次、熔断 5 分钟”。

## 13. 配额处理

流程保持：

```text
输入校验
→ 检查全局容量
→ 生成 requestId
→ 扣费
→ 调用路由器
→ 成功返回
→ 最终失败退款
```

一次业务请求可以有多个供应商 attempt，但用户只扣一次。路由器不得自行扣费或退款。

## 14. 代码改造清单

接入调用池：

```text
TrendAnalysisService
TrendProfileAnalysisService
PlanGenerationService
LaunchCheckService
JobMatchService
OpenSourceAiClient
OpenSourceContributionService
AgentActionService 间接调用的相关 Service
```

迁移到 `TEXT`：

```text
DocumentServiceImpl
LeaderboardRefreshTask
OpenSourceTranslationService
README 和其他翻译处理
```

保持原状：

```text
AiAuditService
JobAiAuditClient
KnowledgeReindexService
Embedding 配置
SystemTaskService
现有审核线程池
```

Agent 设备执行器仍为 2 线程，模型池支持 80 不等于 Agent 同时执行 80 个任务。

## 15. 调用记录

新增 `ai_tool_call_log`：

```text
id
request_id
attempt_no
account_id
business_type
business_id
purpose
provider
slot_no
key_fingerprint
model
status
http_status
error_code
error_summary
prompt_tokens
completion_tokens
started_at
finished_at
duration_ms
create_time
```

不得保存完整 API Key、Authorization、用户完整隐私输入及不必要的完整模型响应。

## 16. 管理端改造

页面分为：

1. AI 工具调用池；
2. DeepSeek 兜底；
3. 文本生成与翻译；
4. AI 托管审核；
5. RAG Embedding；
6. 当前活跃调用；
7. 调用历史。

不再单独显示“做自己的项目”和“参与开源项目”，但后端保留旧配置解析能力。

Key 列表显示编号、掩码、状态、当前任务、用户、运行时长和最近结果。支持替换、启停、单 Key 测试和状态恢复，不返回完整 Key。

汇总显示：

```text
全局运行：62/80
Gitee 运行：9/10
DeepSeek 运行：53
Agent 运行：2/2
近 5 分钟成功率
P50/P95/P99
Token 消耗
当前错误
```

建议接口：

```text
GET  /admin/ai-tool-pool
PUT  /admin/ai-tool-pool/config
PUT  /admin/ai-tool-pool/slots/{slotNo}
POST /admin/ai-tool-pool/slots/{slotNo}/test
POST /admin/ai-tool-pool/slots/{slotNo}/enable
POST /admin/ai-tool-pool/slots/{slotNo}/disable
POST /admin/ai-tool-pool/slots/{slotNo}/reset
GET  /admin/ai-tool-pool/runtime
GET  /admin/ai-tool-pool/calls
```

## 17. 测试流程

### 17.1 静态与构建

- Maven 编译和后端测试；
- 用户端、管理端构建；
- Flyway 迁移验证；
- 检查完整 Key 是否进入代码、日志或产物。

### 17.2 独立功能回归

- 文档、README、排行榜和批量翻译；
- AI 托管和岗位审核；
- Embedding、知识库重建和检索；
- 旧任务配置版本。

### 17.3 路由单元测试

- 单槽位获取和释放；
- 同 Key 不可并发复用；
- 10 个请求占用 10 个不同 Key；
- 第 11 个请求使用 DeepSeek；
- 全局不超过 80；
- 第 81 个请求不扣费并返回繁忙；
- 多实例争抢只有一个成功；
- 错误 `leaseId` 不能释放；
- 崩溃后租约最终释放；
- 401、429、超时和退款语义正确。

### 17.4 逐 Key 测试

每个 Gitee Key 执行 4 次短请求和 2～5 次真实长请求，检查授权、延迟、Token、401/403/404。失败 Key 不进入生产池。

### 17.5 Gitee 组合测试

10 个 Key 各执行 1 个真实长请求并同时启动，再持续运行至少 10 分钟。要求每 Key 只有 1 个请求、无重复占用、成功率不低于 99%。

### 17.6 DeepSeek 阶梯测试

```text
10 → 25 → 50 → 65 → 80
```

每级使用真实长文本，记录成功率、P50/P95/P99、429、5xx、超时、Token 和费用。

### 17.7 最终 10+70 测试

```text
10 个 Gitee 请求
70 个 DeepSeek 请求
总并发 80
```

确认请求真实并发而非本地线程池排队，并验证配额、管理状态、Tomcat、Redis、MySQL和普通接口。

### 17.8 平台全链路

从趋势分析、方案生成、上线体检、岗位匹配、开源匹配和贡献指南真实接口发起。压测使用测试 IP 白名单、多个测试 IP 或内部测试入口，不删除生产限流。

### 17.9 故障与安全

覆盖 Key 失效、429、503、DeepSeek不可用、Redis异常、请求超时、后端重启、动态配置切换和旧版本恢复。确认页面、日志、Redis和数据库均无完整 Key。

## 18. 实施与灰度顺序

1. 新增 `TEXT` 并迁移翻译；
2. 增加调用日志和 Redis 槽位租约；
3. 实现统一路由；
4. 先迁移一个低风险功能；
5. 依次迁移岗位、开源、上线体检、方案和趋势；
6. 修改管理端；
7. 接入并验证 10 个 Gitee Key；
8. 接入 DeepSeek 兜底；
9. 完成组合和全链路测试。

灰度：

```text
阶段一：3 个 Gitee Key，全局 10
阶段二：10 个 Gitee Key，全局 25
阶段三：全局 50
阶段四：全局 65
正式目标：全局 80
```

每阶段至少观察成功率、配额、退款、Key状态和普通接口性能。

## 19. 回滚

新增：

```env
AI_TOOL_POOL_ENABLED=false
AI_TOOL_GLOBAL_MAX_CONCURRENCY=80
```

严重问题时停止新请求、等待运行请求结束、关闭调用池、恢复原动态配置并回滚后端和管理端。保留调用日志并核对配额、退款和未完成请求。旧配置解析至少保留一个完整发布周期。

## 20. 验收标准

正式宣布支持 80 人同时调用前必须满足：

1. 10 个 Gitee Key 全部通过独立测试；
2. 每个 Gitee Key 最多 1 个请求；
3. 第 11 个请求正确进入 DeepSeek；
4. 全局运行数不超过 80；
5. 10+70 组合测试通过；
6. 80 个真实长文本业务请求能够同时执行；
7. 成功率不低于 99%；
8. 无大规模 429；
9. 配额和退款保持幂等；
10. 管理端状态准确；
11. 普通业务接口可用；
12. Tomcat、Redis、MySQL和JVM稳定；
13. 文本翻译、AI审核和Embedding无回归；
14. 日志、数据库和Redis无明文Key；
15. 服务异常后不会永久占用槽位；
16. 旧配置版本可解析；
17. 回滚流程验证通过。

## 21. 第二阶段

第一阶段稳定后再实施AI任务异步化、超过80后的持久化排队、用户排队位置、WebSocket通知、Java 21虚拟线程、独立AI执行器、多实例部署和基于生产数据的熔断及重试。

第二阶段不能直接复用当前最大4线程的通用 `system_task` 执行器。

## 22. 最终结论

项目只依赖用户提供的桌面 `.env.local`，其余接口清单、提示词、测试数据、配额逻辑和超时信息从现有项目自动提取。

实施路线：

```text
拆分文本配置
→ 增加调用记录
→ 建立10个Gitee槽位
→ 增加DeepSeek动态兜底
→ 统一交互式AI路由
→ 修改管理端
→ 逐级压测
→ 灰度提升到80
```

完成全部改造和80并发全链路验收后，OrangeJD才能正式声明具备80名用户同时进行长文本AI调用的能力。
