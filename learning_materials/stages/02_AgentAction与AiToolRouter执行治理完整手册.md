# OrangeVC AI Agent 岗位学习｜阶段 2：AgentAction 与 AiToolRouter 执行治理完整手册

> 源码快照：2026-09-05，Git 68913b5  
> 本阶段目标：讲透 OrangeVC 如何把“不可信的模型动作”变成受权限、确认、状态机、资源池、幂等、扣费、重试、审计和补偿约束的可执行操作。  
> 本文只讨论 AgentAction 与 AiToolRouter，不展开 RAG、Python 训练、LLM 理论或 LangGraph。

---

## 0. 先给出贯穿全章的总定义

OrangeVC 的执行治理分成两层：

~~~text
AgentAction
  负责业务动作治理：
  设备是谁？
  它有权做什么？
  参数是否合法？
  是否需要用户确认？
  任务处于什么状态？
  动作结果怎样保存和查询？

        ↓ 确认后调用具体业务 Service

AiToolRouter
  负责模型调用治理：
  是否已有结果可重放？
  全局还有没有容量？
  使用哪个 Gitee Key？
  Key 不可用时怎样降级？
  哪些错误能重试？
  租约怎样续期和释放？
  调用怎样记录和监控？
~~~

一句话面试定位：

> AgentAction 是“用户授权与业务任务状态机”，AiToolRouter 是“模型资源调度与调用可靠性层”。

二者共同形成 Agent Runtime 的一部分基础能力，但当前还不是通用 Agent Runtime：

- 没有动态 Tool Registry；
- 没有通用 DAG 或图状态机；
- 没有模型自主规划多步任务；
- 没有分布式任务队列；
- 没有工具进程级沙箱；
- 没有完整 Trace/Span；
- AgentAction 的业务动作仍由硬编码 switch 分派。

可以说“具备 Agent 执行治理基础”，不能说“已经构建完整多智能体平台”。

---

# 第一部分：为什么需要两层治理

## 1. 模型输出为什么不能直接执行

连接器或模型可能把一句自然语言分类为：

~~~json
{
  "action": "launch_check.stage.generate",
  "parameters": {
    "projectId": 12,
    "stageNo": 3
  },
  "summary": "生成第三阶段上线体检"
}
~~~

但这份 JSON 仍然是不可信输入，因为模型可能：

- 选错动作；
- 编造 projectId；
- 省略必要参数；
- 把 GitHub Issue 编号误当平台内部编号；
- 生成越权动作；
- 因重复调用造成重复扣费；
- 未经用户同意执行付费或写操作；
- 被 Prompt Injection 诱导执行额外能力。

所以 OrangeVC 没有让连接器直接调用业务 Service，而是设置以下边界：

~~~text
自然语言
  ↓
本地规则/模型只负责“建议动作”
  ↓
MCP Tool 输出结构化 action
  ↓
服务端重新认证、重新鉴权、重新校验
  ↓
只读动作直接完成
付费/写动作进入 PREPARED
  ↓
用户明确确认
  ↓
QUEUED → RUNNING → 业务执行
~~~

重要安全原则：

> 模型只提出动作，服务端才拥有最终执行权。

---

# 第二部分：从语音到 AgentAction 的完整入口

## 2. MCP 连接器提供四个核心工具

连接器的 mcp_server.py 暴露：

| MCP 工具 | 作用 |
|---|---|
| orangevc_agent_prepare | 把用户自然语言转换成动作并准备 |
| orangevc_agent_confirm | 用户明确确认后启动任务 |
| orangevc_agent_status | 查询并长轮询任务状态 |
| orangevc_agent_cancel | 取消尚未开跑的任务 |

连接器的轮询参数：

- RUNNING 状态：QUEUED、RUNNING；
- 一次长轮询最长约 18 秒；
- 每约 2 秒查询一次；
- 未结束时要求继续沿用同一 taskId；
- 任务结束后主动播报 spokenMessage。

这使语音端不必让用户反复问“完成了吗”。

代码证据：

- D:\Orangejd\tools\OrangeVC-XiaoZhi-Connector\orangevc_xiaozhi_connector\mcp_server.py
- D:\Orangejd\tools\OrangeVC-XiaoZhi-Connector\orangevc_xiaozhi_connector\service.py

## 3. 自然语言分类只是第一道筛选

连接器有三种分类方式：

1. RuleFirstRouter：先走确定性规则；
2. OpenAiCompatibleRouter：规则不支持时可调用兼容模型；
3. ClaudeCodeRouter：用严格 JSON Schema 输出动作。

ClaudeCodeRouter 还做了几项限制：

- 不保存会话；
- permission-mode 为 dontAsk；
- tools 设为空；
- 单独工作目录；
- JSON Schema 限制 action、parameters、summary；
- 指令最大 4000 字符；
- 默认约 18 秒超时。

这些设计降低本地分类器越权，但不能替代服务端校验。

## 4. 连接器白名单与服务端白名单

普通 AgentDecision 白名单包括多数动作和 unsupported。服务端 AgentActionSupport 维护最终白名单。

服务端当前允许 14 个动作：

~~~text
quota.balance.read
launch_check.project.list
launch_check.project.create
launch_check.stage.generate
open_source.issue.list
open_source.issue.detail
open_source.issue.job_match
open_source.issue.guide
trend.analysis.generate
trend.profile.generate
plan.phase.generate
agent.task.list
notification.unread.read
leaderboard.github.list
~~~

值得注意的真实边界：

> 普通 Python AgentDecision/ROUTER_SCHEMA 当前没有 trend.profile.generate；该动作由 GuidedConversation 收集 topics、purpose、experience、timeCommitment 后直接调用后端 prepare。

因此“画像式 V2 分析”已能走设备链路，但不是通过普通单句动作分类器完成，而是通过引导会话完成。

## 5. 服务端不信任模型给出的 summary

连接器会提交 action、parameters、summary，但 AgentActionPreparationService 只读取 action 和 parameters。

服务端根据实际业务数据重新构造：

- actionSummary；
- quotaCost；
- spokenMessage；
- result；
- confirmation 文案。

这意味着模型不能通过伪造 summary 隐藏真实操作或伪造价格提示。

这是很好的面试亮点：

> 自然语言层只负责意图建议，所有面向用户的执行摘要、价格和结果描述都由可信服务端生成。

---

# 第三部分：设备身份与权限边界

## 6. 设备不是直接复用用户网页登录 JWT

设备接入有独立凭据链：

~~~text
已登录用户创建绑定码
  ↓
8 位绑定码，10 分钟有效
  ↓
设备交换绑定码
  ↓
服务器生成 ojd_ 开头的随机 Token
  ↓
只把 Token 原文返回一次
  ↓
数据库只保存 SHA-256 Token Hash 和末尾 Hint
  ↓
设备以后使用 Bearer Token
~~~

设备 Token：

- 随机 32 字节再 Base64 URL 编码；
- 有效期约 90 天；
- 可撤销；
- 每次认证会更新 last_used_at；
- 数据库不保存原文。

## 7. Interceptor 建立可信身份

AgentDeviceInterceptor：

1. 读取 Authorization；
2. 调用 AgentDeviceAuthService.authenticate；
3. 根据 Token Hash 查 ACTIVE 且未过期的凭据；
4. 把 accountId 放入 BaseContext；
5. 把 credentialId 和 scopes 写入 request attribute；
6. 请求结束清理 BaseContext。

AgentActionController 不接收客户端声称的 accountId 或 credentialId，而是只使用拦截器建立的可信身份。

## 8. 两道权限校验

服务端 prepare 同时检查：

~~~text
action 必须存在于 ALLOWED_ACTIONS
并且
当前设备 scopes 必须包含该 action
~~~

两者意义不同：

- Allowlist：系统是否支持这项能力；
- Scope：这台设备是否被授予这项能力。

当前边界：

- 数据模型和校验支持 scope；
- 但 exchange 时所有设备获得同一份静态 SCOPES；
- 暂未看到按设备配置细粒度不同 scope 的管理流程。

所以可以说“实现 Scope 校验机制”，不能说“已经实现成熟的按设备最小权限配置平台”。

---

# 第四部分：AgentAction 支持哪些动作

## 9. 七个立即完成动作

这些动作在 prepare 内同步执行并直接写入 SUCCEEDED，不要求确认：

| 动作 | 行为 |
|---|---|
| quota.balance.read | 查询配额余额 |
| agent.task.list | 查询最近语音任务，limit 限制到 1 至 10 |
| notification.unread.read | 查询未读通知数量 |
| leaderboard.github.list | 查询 GitHub 榜单，最多返回 5 条 |
| launch_check.project.list | 查询用户上线体检项目 |
| open_source.issue.list | 查询可参与 Issue |
| open_source.issue.detail | 查询指定 Issue 详情 |

为什么只读动作也保存 task：

- 用户能查看历史；
- 语音端有统一 taskId 和 response；
- 操作具有审计记录；
- 上层不需要为只读和异步动作维护两种完全不同的协议。

## 10. 七个必须确认的动作

| 动作 | 原因 | 预计费用 |
|---|---|---|
| launch_check.project.create | 会写业务数据 | 0，但仍确认 |
| launch_check.stage.generate | 付费且调用 AI | 动态价格 |
| open_source.issue.job_match | 付费且调用 AI | 动态价格 |
| open_source.issue.guide | 付费且调用 AI | 动态价格 |
| trend.analysis.generate | 付费且调用 AI | 动态价格 |
| trend.profile.generate | 付费且调用 AI | 动态价格 |
| plan.phase.generate | 付费且调用 AI | 动态价格 |

关键判断：

> 是否需要确认不只由价格决定。创建项目即使费用为 0，因为有写入副作用，也进入 PREPARED。

这比简单规则“cost > 0 才确认”更符合 Agent 工具安全。

---

# 第五部分：prepare 到底做了什么

## 11. prepare 的九个步骤

~~~text
1. 读取 action，最多 64 字符
2. 校验服务端动作白名单
3. 校验设备 Scope
4. 要求 parameters 必须是对象
5. 查询当前余额
6. 按 action 校验业务参数
7. 读取可信业务对象或前序结果
8. 读取当前价格，生成服务端摘要
9. 只读动作建 SUCCEEDED；写/付费动作建 PREPARED
~~~

## 12. 参数校验示例

### 创建体检项目

- projectName 最多 128 字符；
- projectSummary 最多 10000 字符；
- 项目名不能为空；
- 项目说明至少 20 字。

### 生成体检阶段

- projectId 必须可转数字；
- stageNo 必须在 1 至 8；
- 服务端读取这个用户的项目详情；
- stageNo > 1 时必须已经存在上一阶段；
- 价格从 PricingService 读取。

### Issue 付费动作

- issueId 必须是数字；
- 服务端重新读取 Issue；
- 标题和价格由服务端生成。

### 趋势 V1

- question 最多 2000 字符；
- 至少 5 字；
- 价格使用 ai:trend:v1。

### 趋势画像 V2

- topics 必须是非空数组；
- purpose、experience、timeCommitment 必填；
- 单项最多 200 字；
- 价格从 TrendProfileAnalysisService.cost() 获取。

### 方案分阶段生成

- phase 必须在 1 至 4；
- 第一阶段要求 summary 至少 10 字；
- 第二至第四阶段要求前序内容；
- 如果设备没提交 summary/prevContents，服务端从同一账号、同一设备最近成功的 plan.phase.generate 任务继承；
- 前一条结果的 phase 必须刚好是当前 phase - 1。

这个继承机制让用户可以说“继续下一阶段”，无需语音重复长文本。

## 13. 参数冻结

PREPARED 任务会把 parameters 序列化到 parameters_json。

用户确认时只提交 taskId，不重新提交业务参数。执行线程从数据库读取原参数。

这保证：

- 用户确认的是 prepare 时展示的那组参数；
- 模型不能在 confirm 时偷偷换 projectId 或 stageNo；
- 重复 confirm 不会注入新参数；
- 历史记录可以还原当时准备执行的内容。

---

# 第六部分：AgentAction 状态机

## 14. 状态定义

~~~text
PREPARED
  已校验并冻结参数，等待用户确认，5 分钟有效

QUEUED
  已确认，等待本地线程池领取

RUNNING
  工作线程已经开始执行业务

SUCCEEDED
  业务完成，结果已写入任务

FAILED
  执行失败或服务重启中断

CANCELLED
  用户在 PREPARED/QUEUED 阶段取消

EXPIRED
  PREPARED 超过确认时间
~~~

## 15. 完整状态图

~~~text
只读动作：
  prepare ─────────────────────────────→ SUCCEEDED

写入/付费动作：
  prepare → PREPARED
               │
               ├─ confirm 未过期 → QUEUED → RUNNING → SUCCEEDED
               │                              └──────→ FAILED
               │
               ├─ cancel ─────────→ CANCELLED
               │
               └─ 超过 5 分钟 ───→ EXPIRED

QUEUED：
  ├─ worker 抢到 → RUNNING
  └─ cancel 抢到 → CANCELLED
~~~

终态：

~~~text
SUCCEEDED
FAILED
CANCELLED
EXPIRED
~~~

---

# 第七部分：confirm 为什么不会重复启动

## 16. confirm 的控制流

~~~text
1. 按 taskId + accountId + credentialId 查询任务
2. 若已是 QUEUED/RUNNING/SUCCEEDED/FAILED，直接返回当前状态
3. 非 PREPARED 且不在上述集合，拒绝确认
4. 检查 expiresAt
5. 已过期则条件更新为 EXPIRED
6. 执行条件更新 PREPARED → QUEUED
7. 只有更新行数等于 1 的请求才提交线程池
8. 重新读任务并返回
~~~

关键 SQL：

~~~sql
UPDATE agent_action_task
SET status = 'QUEUED'
WHERE id = ?
  AND status = 'PREPARED'
  AND expires_at > NOW()
~~~

## 17. 两个 confirm 同时到达会怎样

假设请求 A 和 B 都先读到 PREPARED：

~~~text
A：UPDATE ... WHERE status=PREPARED → 更新 1 行
B：UPDATE ... WHERE status=PREPARED → 更新 0 行
~~~

只有 A 会 executor.execute()。

这是一种 Compare-And-Set 状态转换：旧状态必须仍为 PREPARED，才能变为 QUEUED。

正确面试表述：

> 重复确认的幂等不是靠 Java if，而是靠数据库条件更新。Java 预检查改善返回语义，真正防竞态的是 UPDATE 中携带旧状态条件。

## 18. 当前测试边界

当前 AgentActionServiceTest 验证了：

- 只读动作直接成功；
- 付费或写动作只准备、不提前执行；
- 价格读取；
- V2 画像准备；
- 方案阶段继承；
- 历史删除权限。

但它没有直接用真实数据库并发线程测试两个 confirm。

因此：

- 原子性有 SQL 代码证据；
- 还缺真实数据库并发集成测试作为更强证明。

---

# 第八部分：异步执行和取消竞态

## 19. 本地执行器

AgentActionExecutionService 使用：

~~~text
Executors.newFixedThreadPool(2)
线程名 agent-action-worker
daemon = true
~~~

确认成功后任务进入本地线程池。

执行前再次做原子转换：

~~~sql
UPDATE agent_action_task
SET status = 'RUNNING'
WHERE id = ?
  AND status = 'QUEUED'
~~~

只有更新 1 行才真正执行业务。

## 20. confirm 与 cancel 竞态

cancel 只允许：

~~~sql
status IN ('PREPARED', 'QUEUED')
~~~

当任务已经 QUEUED：

### 情况 A：cancel 先执行

~~~text
QUEUED → CANCELLED 成功
worker 再尝试 markRunning
WHERE status=QUEUED 不成立
worker 直接返回
业务不执行
~~~

### 情况 B：worker 先执行

~~~text
QUEUED → RUNNING 成功
cancel 条件不成立
取消失败
业务继续
~~~

因此没有“任务已经 RUNNING 还被标取消”的状态撕裂。

正确理解：

> 当前 cancel 是抢占开跑资格，不是中断一个已经运行的外部模型请求。

## 21. 为什么 RUNNING 不能取消

一旦调用外部模型或业务写操作：

- HTTP 请求可能已经发出；
- Provider 可能已经计算；
- 配额可能已经扣除；
- 数据库写入可能已经发生；
- Java Thread.interrupt 也不能保证远端撤销。

如果想支持运行中取消，需要：

- 下游支持 cancellation token；
- HTTP 客户端可取消；
- 业务步骤可补偿；
- 状态机增加 CANCELLING；
- 明确定义费用规则；
- 防止“本地说取消、远端实际完成”。

---

# 第九部分：真正执行时发生什么

## 22. worker 执行步骤

~~~text
1. QUEUED → RUNNING
2. 把 accountId 写入 BaseContext
3. 按账号和设备重新读取任务
4. 解析冻结的 parameters_json
5. 根据 actionKey 调用具体业务 Service
6. 业务 Service 可能进入 AiToolRouter
7. 成功后读取最新余额
8. RUNNING → SUCCEEDED，写 result_json 和 spoken_summary
9. 异常则 QUEUED/RUNNING → FAILED
10. finally 清理 BaseContext
~~~

## 23. 七类异步动作映射

| actionKey | 下游调用 |
|---|---|
| launch_check.project.create | LaunchCheckService.create |
| launch_check.stage.generate | LaunchCheckService.generate |
| open_source.issue.job_match | OpenSourceContributionService.jobMatch |
| open_source.issue.guide | OpenSourceContributionService.generateGuide |
| trend.analysis.generate | TrendAnalysisService.analyze |
| trend.profile.generate | TrendProfileAnalysisService.analyze |
| plan.phase.generate | PlanGenerationService.generate |

对于趋势 V1，如果 result 中含 error，AgentAction 会主动转成异常，避免把错误 Map 标为 SUCCEEDED。

## 24. 失败时 AgentAction 保存什么

worker 捕获 Exception 后：

~~~text
status = FAILED
errorCode = ACTION_FAILED
spokenMessage =
  任务执行失败。本次未产生最终结果；
  如已扣配额，OrangeVC 会按原业务规则自动退回。
~~~

注意：

> AgentAction 本身不直接执行退款，它依赖各业务 Service 和 AiResultRecordService 的原业务补偿。

所以这句话表达的是业务约定，不是 AgentAction 自己验证退款成功。

---

# 第十部分：过期、历史和服务重启

## 25. PREPARED 怎样过期

PREPARED 的 expiresAt 是创建时间加 5 分钟。

过期处理发生在：

- confirm；
- status 查询。

status 发现已过期，会执行条件更新 PREPARED → EXPIRED。

当前没有看到定时扫描器主动把所有过期 PREPARED 批量转为 EXPIRED。因此一个从未再被查询的过期任务，数据库里可能仍保持 PREPARED，只是逻辑上已不可确认。

## 26. 服务重启恢复

AgentActionService 在 PostConstruct 中调用：

~~~sql
UPDATE agent_action_task
SET status = 'FAILED',
    error_code = 'SERVER_RESTARTED'
WHERE status IN ('QUEUED', 'RUNNING')
~~~

它没有恢复执行，而是明确失败并提示：

~~~text
任务因 OrangeVC 服务重启而中断，
请在网页核对结果和配额记录。
~~~

这是保守恢复：

- 不会盲目重复副作用；
- 用户知道任务状态不确定；
- 但不会自动续跑；
- 也不会由这条 SQL 自动退款。

## 27. 一个重要崩溃窗口

可能发生：

~~~text
AgentAction = RUNNING
  ↓
下游 Router 已完成模型调用、扣费、保存 ai_result_record
  ↓
进程在 AgentAction markSucceeded 之前崩溃
  ↓
重启后 AgentAction 被标 FAILED / SERVER_RESTARTED
~~~

此时：

- AI 结果可能已存在；
- 配额可能已消费；
- 外层设备任务却显示 FAILED。

这正是恢复提示要求用户检查网页 AI 记录和配额的原因。

更完善的方案：

- Agent taskId 作为下游统一业务幂等身份；
- AgentAction 保存 aiResultRecordId/quotaOperationId；
- 重启时对 RUNNING 任务做 reconcile；
- 若下游结果存在则补标 SUCCEEDED；
- 若无结果但已扣费则退款；
- 若状态未知则进入 NEEDS_REVIEW，而不是一律 FAILED。

---

# 第十一部分：AgentAction 当前真实边界

## 28. 它已经实现了什么

### 已实现

- 独立设备凭据；
- Token Hash 存储；
- 服务端动作白名单；
- Scope 校验；
- 参数和业务对象校验；
- 只读/写入/付费动作分级；
- prepare/confirm 二阶段确认；
- 5 分钟确认窗口；
- 参数冻结；
- 数据库条件状态转换；
- 异步执行；
- 状态、历史、取消；
- 重启中断标记；
- 服务端生成安全摘要。

### 部分实现

- Scope：有校验，但设备默认拿到同一套全量 scope；
- 恢复：能识别重启中断，但不能自动 reconcile/续跑；
- 幂等：confirm 层能防重复启动，但没有贯穿到下游 requestId；
- 取消：只能阻止尚未 RUNNING 的任务；
- 审计：有任务表，但没有通用 step/attempt 事件表。

### 尚未实现

- 动态 Tool Registry；
- 每个 Tool 独立 Handler；
- 通用 Tool Schema 注册；
- 分布式工作队列；
- 多实例 claim/lease；
- AgentAction 自动重试；
- DLQ；
- 运行中取消；
- 人工审核状态；
- 通用 Saga/补偿编排；
- Tool 沙箱。

## 29. 本地线程池的扩展风险

固定两线程意味着同时只执行两个设备动作，但 Executors.newFixedThreadPool 默认使用无界队列。

风险：

- 高峰时 QUEUED 不断积压；
- 没有明确队列长度和拒绝策略；
- 用户看到已排队但不知道预计等待；
- 应用重启时所有排队任务被标失败；
- 多实例时每实例各自两线程，调度不可全局观察。

改进：

- 使用有界 ThreadPoolExecutor；
- 记录 queue position/estimatedWait；
- 拒绝时把任务恢复或标明确失败；
- 更进一步迁到持久化队列；
- 使用 claim lease 和心跳；
- 把任务重试与 Provider 重试分开。

## 30. 价格只是预览，没有冻结或预占

prepare 中：

- 查询当前 balance；
- 查询当前 cost；
- 保存 quota_cost 和 balance_snapshot。

confirm 时没有冻结资金。执行时下游业务 Service 会再次读取当前 PricingSnapshot 并真正扣费。

因此在 5 分钟窗口内：

- 余额可能被其他请求消耗；
- 价格可能发生变化；
- prepare 展示的预计费用不一定等于最终扣费；
- 任务可能在确认后因为余额不足失败。

正确说法：

> AgentAction 保存的是价格和余额快照，不是资金预占，也没有把 priceVersion 固定传给下游。

改进可以选择：

1. confirm 时按任务快照预占配额；
2. task 保存 priceVersion；
3. 执行时按确认的价格版本扣费；
4. 若价格已变化，要求重新确认；
5. 释放过期任务的预占额度。

---

# 第十二部分：AiToolRouter 的职责边界

## 31. Router 不负责什么

AiToolRouter 不负责：

- 理解自然语言；
- 选择业务 action；
- 构造 Prompt；
- 解析业务 JSON；
- 判断业务结果是否完整；
- 决定业务价格；
- 直接实现配额账本；
- 调度 AgentAction 状态。

它负责的是模型调用公共运行语义。

## 32. AiToolContext

每次调用携带：

~~~text
purpose
requestId
accountId
businessType
businessId
legacyConfigType
requestHash
~~~

作用：

- requestId：结果和 attempt 身份；
- requestHash：请求内容摘要；
- accountId：结果归属；
- businessType/businessId：分类和展示；
- purpose：审计语义；
- legacyConfigType：池关闭时选择旧配置。

AiToolContext 会做长度规范化：

- purpose/businessType 最多 64；
- requestId/businessId 最多 128；
- legacyConfigType 最多 32；
- requestHash 最多 64；
- 缺 requestId 时自动 UUID。

注意：截断用于保护字段长度，但调用方仍应生成不会碰撞的规范业务身份。

---

# 第十三部分：AiToolRouter 一次成功调用

## 33. 总链路

~~~text
execute(context, operation)
  ↓
1. findExisting：查成功结果
  ↓ 未命中
2. attemptNo = 1
  ↓
3. 检查调用池开关
  ↓ enabled
4. 检查池配置完整
  ↓
5. 获取全局租约
  ↓
6. 尝试获取一个 Gitee Key 槽
  ├─ 成功：GITEE
  └─ 全部不可用：TOOL_FALLBACK / DEEPSEEK
  ↓
7. 插入 RUNNING attempt 日志
  ↓
8. 每 30 秒续租
  ↓
9. operation.run(config)
  ↓
10. saveRequired 保存结果
  ↓
11. attempt 日志标 SUCCEEDED
  ↓
12. success metrics +1
  ↓
13. finally 取消续租并释放租约
~~~

---

# 第十四部分：先重放结果，再占容量

## 34. 为什么 result replay 放在最前面

Router 第一行核心逻辑是：

~~~text
existing = resultRecorder.findExisting(context)
if existing != null:
    return existing
~~~

命中后不会：

- 获取 Redis 全局租约；
- 占用 Key 槽；
- 调用 Provider；
- 再次保存结果。

这同时节省：

- 模型费用；
- Provider RPM/并发；
- 用户等待时间；
- 服务器连接资源。

## 35. 结果怎样查

AiResultRecordService 只对 accountId > 0 的请求重放。

查询条件：

~~~text
requestId
accountId
requestHash
~~~

结果表以 request_id 为唯一键。

重要边界：

> Router/ResultRecorder 自身没有显式抛出“同 requestId 不同 requestHash”的冲突。不同 hash 会表现为未命中；对付费业务，后续配额 operation 的 validateReplay 会拒绝不同 payload。若某个 Router operation 不经过配额校验，结果 upsert 可能覆盖同 requestId 的旧结果。

因此真正强一致的幂等冲突保护目前依赖“付费业务配额层也使用同一 identity”。

## 36. 它不是真正的 single-flight

真正的 single-flight 应当是：

~~~text
相同 requestId 首次并发进入
  ↓
只有一个成为 owner
其他请求等待 owner
  ↓
owner 完成后共同读取同一结果
~~~

当前只会重放已经持久化的结果。

调用池开启时，ai_tool_call_log 的唯一键是 request_id + attempt_no。两个相同 requestId 同时首次进入时：

- 二者都可能先 miss result；
- 一个插入 attempt 1 成功；
- 另一个可能在日志唯一键处失败；
- 它不会等待第一个完成后重放。

调用池关闭时没有 attempt 日志，这层偶然防重也不存在，两个请求可能都进入 operation。

所以准确结论：

> 当前有 completed-result replay，但没有协调中的 in-flight request。

改进：

- Redis SET NX requestId owner lock；
- owner 心跳；
- waiter 等待结果或状态；
- 超时后安全接管；
- 数据库 operation claim；
- 明确同请求失败后的重试语义。

---

# 第十五部分：调用池关闭与开启

## 37. enabled=false

池关闭时：

~~~text
使用 legacyConfigType 获取旧配置
直接 operation.run
保存结果
不获取 Redis 租约
不写 ai_tool_call_log
不记录 Router success/failure metrics
~~~

但外层 execute 的自动重试循环仍然存在，只要 autoRetryEnabled 且异常被判定可重试。

用途：

- 灰度迁移；
- 向后兼容；
- 没有池配置时继续用旧单配置。

边界：

- 无全局容量治理；
- 无 Key 槽治理；
- 无 attempt 审计；
- 并发幂等更弱。

## 38. enabled=true 但 poolReady=false

poolReady 要求：

- Gitee endpoint 非空；
- model 非空；
- 至少一个 Key。

不满足时直接失败：

~~~text
AI 工具调用池尚未完整配置
~~~

poolReady 只验证 Gitee 池，不验证 fallback 配置能否实际调用。

---

# 第十六部分：两级租约

## 39. 全局租约

Redis Key：

~~~text
orangevc:ai-tool:global:leases
~~~

数据结构：Sorted Set。

- member：随机 leaseId；
- score：到期时间戳；
- 获取时先删除过期成员；
- ZCARD 达到 globalMaxConcurrency 就拒绝；
- 未达到则原子 ZADD。

配置范围：

- globalMaxConcurrency：1 至 80，默认 50；
- acquireWaitMillis：0 至 10000，默认 2000；
- leaseMillis：60000 至 600000，默认 330000。

获取不到容量时，Router 每次最多短睡约 50 ms，直到等待期限，最后：

- 指标 orange.ai.tool.capacity.rejected +1；
- 抛出“AI 服务繁忙，请稍后重试”。

## 40. Key 槽租约

每个配置 Key 是一个 slot，最多支持 10 个环境变量槽位。

Redis Key：

~~~text
orangevc:ai-tool:slot:{slotNo}
~~~

槽位获取 Lua 同时检查：

~~~text
disabled:{slotNo}
invalid:{slotNo}
throttled:{slotNo}
slot:{slotNo} 是否已经被占用
~~~

全部不存在才 PSETEX 当前 slot。

这保证同一个 Key 槽在租约有效期内最多被一个请求占用。

## 41. 轮询方式

AiToolSlotLeaseService 使用本 JVM 的 AtomicInteger cursor 选择起点，再循环所有 slot。

这是 round-robin 起点加 Redis 全局独占：

- 本地 cursor 改善本实例分布；
- Redis Lua 防止不同实例同时拿同一 slot。

它不是按 Key 当前 RPM、TPM、延迟或历史成功率做智能调度。

## 42. 为什么还需要全局租约

Key 槽限制的是 Gitee Key 并发。全局租约限制所有 Provider 总调用量，包括 fallback。

如果有 10 个 Gitee Key：

- Gitee 同时最多约 10 个 slot 请求；
- 全局上限可配置到最多 80；
- Gitee 槽全忙时，其余已获全局租约的请求可进入 fallback；
- fallback 没有单独的并发上限，主要受全局上限约束。

不能说“10 个 Key 就能稳定支持 80 个模型并发”。80 是代码硬上限，不是压测证明的承载量。

---

# 第十七部分：fallback 路由

## 43. 什么时候使用 DeepSeek fallback

只要 acquireSlot 返回空，就使用：

~~~text
AiRuntimeConfigService.TOOL_FALLBACK
provider = DEEPSEEK
~~~

“空”可能表示：

- 所有槽都 BUSY；
- 槽被管理员 DISABLED；
- 401/403 后被标 INVALID；
- 429 后处于 THROTTLED；
- 没有可获取槽。

fallback 配置可来自：

- Redis 动态配置版本；
- Redis 不可用时环境变量灾备；
- 默认复用 trend 环境配置。

## 44. fallback 的边界

- 没有独立 slot；
- 没有单独的 throttled/invalid 状态；
- 401、429 等失败不能隔离某个 fallback Key；
- poolReady 不证明 fallback endpoint/key/model 完整；
- runtime 中 fallbackRunning 是 globalRunning - giteeRunning 的推算，不是独立租约计数。

---

# 第十八部分：调用日志和敏感信息

## 45. attempt 日志

每次 attempt 插入：

~~~text
requestId
attemptNo
accountId
businessType
businessId
purpose
provider
slotNo
keyFingerprint
model
status=RUNNING
startedAt
~~~

结束后更新：

~~~text
status
httpStatus
errorCode
errorSummary
finishedAt
durationMs
~~~

状态可能是：

~~~text
RUNNING
RETRY_WAIT
SUCCEEDED
TIMEOUT
FAILED
~~~

## 46. 为什么只记 Key fingerprint

Key fingerprint：

~~~text
SHA-256(apiKey) 的前 12 个十六进制字符
~~~

用途：

- 判断问题集中在哪个 Key；
- 不把原始 Key 写入数据库或接口；
- 管理端可查看槽位状态。

管理接口明确只展示 fingerprint，不返回 Key 原文。

## 47. 日志隐私边界

ai_tool_call_log 不保存：

- Prompt；
- Response；
- 原始 Key。

safeMessage 会：

- 把 Bearer Token 替换为 Bearer <redacted>；
- 最多保存 500 字符。

边界：

- 只明确脱敏 Bearer 模式；
- 其他异常消息若包含敏感参数，仍可能进入 errorSummary；
- prompt_tokens/completion_tokens 列存在，但当前 Router mapper 没有写入它们；
- 有 requestId 和 attempt，不等于完整分布式 Trace。

---

# 第十九部分：租约续期与释放

## 48. 为什么租约要有 TTL

如果进程崩溃，finally 不会执行。没有 TTL 时：

- 全局容量永久减少；
- Key 槽永久 BUSY。

TTL 使 Redis 状态最终自愈。

## 49. 续租

operation 执行期间，每 30 秒：

- 更新全局租约到期 score；
- 如果有 Gitee slot，只有 Redis 中 value 仍等于本请求 lease value 才延长 TTL。

默认 lease 是 330 秒，续租周期 30 秒。

## 50. compare-value 释放为什么重要

slot 释放 Lua：

~~~text
只有 GET slotKey == 当前 lease value
才 DEL
~~~

场景：

1. 请求 A 租约过期；
2. 请求 B 获取同一 slot；
3. A 迟到执行 finally；
4. 如果直接 DEL，A 会误删 B 的租约；
5. compare-value 使 A 无权删除 B 的 value。

这是标准的租约所有权保护。

## 51. 续租失败会怎样

renewSafely 只记录错误，operation 继续。

如果 Redis 长时间失败或续租延迟超过 TTL：

- 全局租约可能过期；
- slot 可能被新请求获取；
- 原请求其实仍在调用；
- 实际并发可能超过配置；
- 同一 Key 可能短暂被并发复用。

所以 lease 是故障下的“尽力互斥”，不是数学上的永不重叠。

## 52. 释放失败会怎样

releaseSafely 也只记日志，不让已经成功的业务因为释放失败变成失败。

残留租约会依靠 TTL 过期。

这是一种取舍：

- 优先保证已生成结果可交付；
- 接受短时间容量少算；
- 依赖 TTL 自愈。

---

# 第二十部分：自动重试

## 53. 重试外循环

~~~text
attemptNo = 1
while true:
    try:
        executeAttempt
        return result
    catch exception:
        if 不可重试:
            throw
        delay = 退避时间
        sleep
        attemptNo++
~~~

每个 attempt 都会：

- 重新获取全局租约；
- 重新选择 slot/fallback；
- 重新写 attempt 日志；
- 执行 operation；
- 最后释放租约。

重要结论：

> 重试等待期间已经释放全局和 slot 租约，不会用 backoff 睡眠占住模型容量；但原 HTTP 请求线程或 AgentAction worker 线程仍被 Thread.sleep 占用。

## 54. 哪些 HTTP 状态可重试

~~~text
401
403
408
409
425
429
所有 5xx
~~~

为什么 401/403 也重试：

- 当前 Gitee Key 可能失效；
- 标记该 slot INVALID；
- 下一 attempt 尝试其他 Key 或 fallback。

它不是对同一个失效 Key 无脑重试的设计意图。

## 55. 其他可重试错误

- SocketTimeoutException；
- HttpTimeoutException；
- 特定业务消息，如：
  - 系统将自动重试；
  - AI 分析/调用/匹配/生成失败；
  - AI 返回不完整；
  - 候选不足；
  - AI 服务繁忙；
  - 请求过于频繁。

这是“异常类型 + 文本启发式”的混合策略。

边界：

- 文案变更可能改变重试行为；
- 国际化或乱码会影响匹配；
- 更稳妥的是统一错误码和 RetryDisposition。

## 56. 明确不重试

- HTTP 400 等永久请求错误；
- 消息包含“已退回”；
- 消息包含“结果保存失败”。

结果持久化失败不重试的原因：

- AiResultRecordService 已尝试退款；
- 再次执行可能重新调用模型；
- 原 requestId 已处于退款状态；
- 应让用户发起新的业务请求。

## 57. 退避算法

若 Provider 给 Retry-After：

~~~text
delay = min(autoRetryMaxDelay, retryAfter)
~~~

否则：

~~~text
delay = initialDelay × 2^(attemptNo-1)
再截到 maxDelay
最多左移 10 次
~~~

默认：

~~~text
initial = 2000 ms
max = 30000 ms
~~~

大致为：

~~~text
2s → 4s → 8s → 16s → 30s → 30s...
~~~

当前没有随机抖动 jitter，多请求同时失败时可能一起醒来，产生惊群。

## 58. maxAttempts=0 的真实含义

配置默认：

~~~text
auto-retry-max-attempts = 0
~~~

代码含义：

~~~text
0 = 不限制次数
~~~

不是“不重试”。

风险：

- Provider 长期失败时同步请求可能一直重试；
- AgentAction 两个 worker 可能都被长期占住；
- 前端或连接器等待时间拉长；
- fallback 401/429 没有 slot 隔离时尤其危险；
- attempt 日志持续增长。

生产建议：

- 设置有限最大 attempt；
- 设置整个任务 deadline；
- 区分用户同步接口与后台任务；
- 加 jitter；
- 熔断 Provider；
- 超过预算后退款并返回可重试任务状态。

---

# 第二十一部分：不同错误如何治理 Key

## 59. 401/403

如果当前使用 Gitee slot：

~~~text
slot → INVALID
~~~

INVALID 没有 TTL，需要管理员 reset。

这是合理的，因为鉴权错误通常不会等几十秒自动恢复。

## 60. 429

如果当前使用 Gitee slot：

~~~text
slot → THROTTLED
TTL = Retry-After
~~~

若 Retry-After <= 0，setThrottled 不会创建状态键，下一 attempt 可能很快再次选到同一 slot。

改进：

- Retry-After 缺失时使用默认冷却；
- 按 Provider 和 Key 维护限流状态；
- 结合真实配额窗口。

## 61. 其他可重试错误

当前 slot 会被临时 THROTTLED：

~~~text
max(5 秒, min(30 秒, retryDelay + 5 秒))
~~~

目的是让下一 attempt 优先选择其他 Key。

## 62. fallback 错误

slot 为 null，因此不会设置任何 Key 状态。

如果 fallback 持续 401/429，且 maxAttempts=0：

- 仍被判可重试；
- 每次仍选择同一 fallback；
- 可能无限循环。

这是当前最需要收口的 Router 风险之一。

---

# 第二十二部分：成功、失败和结果保存顺序

## 63. 成功顺序

~~~text
operation 返回
  ↓
AiResultRecorder.saveRequired
  ↓
attempt 标 SUCCEEDED
  ↓
success metric
  ↓
返回调用方
~~~

为什么先保存结果再标成功：

- 防止日志成功但结果不可重放；
- 保存失败时能进入异常路径；
- AiResultRecordService 会尝试退款。

## 64. 结果保存失败

AiResultRecordService：

1. 序列化结果；
2. 最大约 8,000,000 个 Java 字符；
3. upsert ai_result_record；
4. 失败时根据 businessType 尝试退款；
5. 抛出“结果保存失败，本次额度已自动退回”。

Router：

- 将该 attempt 标 FAILED；
- RetryPolicy 识别“结果保存失败/已退回”；
- 不再自动重试；
- finally 释放租约。

## 65. 调用日志失败的不同处理

### insertLog 失败

insert 发生在 operation 前。

- 直接抛异常；
- operation 不执行；
- finally 释放租约；
- 当前设计是审计 fail-closed。

### finishLog 失败

finish 被 try/catch 包住。

- 记录服务器错误日志；
- 不影响已经完成的业务结果；
- attempt 行可能长期保持 RUNNING；
- 当前设计是结束审计 best-effort。

这两个选择有意不同：

> 没有开始审计就不启动高成本调用；但调用完成后，不能因为结束日志失败而丢弃用户结果。

---

# 第二十三部分：Router 与配额为什么不会在重试中重复扣费

## 66. operation 内部通常先 consume

以 PlanGenerationService 为例：

~~~text
构造一次 QuotaRequestIdentity
  ↓
构造一次 AiToolContext
  ↓
Router.execute(context, operation)
  ↓
每个 attempt 再次进入 operation
  ↓
operation 调 quotaService.consume(identity)
~~~

虽然 operation 可能执行多次，但所有 attempt 复用同一个 identity.requestId 和 requestHash。

QuotaService：

- 锁账户；
- 按 requestId 查 quota_operation；
- 已 CONSUMED 时验证 accountId、scope、requestHash；
- 验证通过后返回原 charge；
- 不再次扣费。

因此：

> Router 的“至少一次 operation 执行”通过配额层业务幂等，避免“至少一次扣费”。

## 67. 为什么失败时不能每次 attempt 都退款

如果是可重试错误：

- 本 attempt 虽失败；
- 下一 attempt 还要使用同一 quota operation；
- 若先退款，下一 attempt 会遇到 REFUNDED 并失败。

所以业务 Service 会判断：

~~~text
retryManaged = Router 存在 && RetryPolicy 认为可重试
~~~

- 可重试：暂不退款，让 Router 继续；
- 最终永久失败：业务 Service 退款；
- 结果保存失败：AiResultRecordService 退款。

这是 Router 和业务层之间一个隐式协议。

边界：

- RetryPolicy 被两边共同调用；
- 如果错误分类不一致或文案变化，可能出现该退未退或提前退款；
- 更好方案是结构化 AttemptResult 和统一补偿协调器。

---

# 第二十四部分：AgentAction 与 Router 如何串起来

## 68. 一次付费设备动作全链路

~~~text
用户语音
  ↓
连接器分类 action/parameters
  ↓
POST /agent/actions/prepare
  ↓
设备 Token 认证 + Scope
  ↓
服务端参数和业务对象校验
  ↓
agent_action_task = PREPARED
  ↓
用户明确确认
  ↓
POST /agent/actions/{taskId}/confirm
  ↓
PREPARED → QUEUED
  ↓
两线程本地 executor
  ↓
QUEUED → RUNNING
  ↓
调用具体业务 Service
  ↓
业务 Service 创建 QuotaRequestIdentity
  ↓
AiToolRouter 结果重放/租约/Key/attempt
  ↓
operation 内配额幂等扣费 + Provider 调用
  ↓
ai_result_record 保存
  ↓
Router 返回业务结果
  ↓
AgentAction RUNNING → SUCCEEDED
  ↓
设备 status 长轮询得到结果
~~~

## 69. 两个状态机是嵌套的

外层 AgentAction：

~~~text
PREPARED → QUEUED → RUNNING → SUCCEEDED/FAILED
~~~

内层 AiToolRouter attempt：

~~~text
RUNNING → RETRY_WAIT
RUNNING → RETRY_WAIT
RUNNING → SUCCEEDED
~~~

Router 重试期间：

- 外层 AgentAction 一直是 RUNNING；
- 用户不需要再次确认；
- 每个 attempt 重新租赁资源；
- 配额不重复扣；
- 最终成功才把外层任务标 SUCCEEDED；
- 最终抛错则外层标 FAILED。

## 70. 两层 request identity 没有贯通

AgentAction 的 taskId 是 UUID，但 worker 调下游 Service 时，没有把 taskId 作为 Idempotency-Key 或 QuotaRequestIdentity。

由于 worker 不在 Servlet RequestContext 中：

- QuotaRequestIdentity.current 读不到 Idempotency-Key header；
- 它为下游生成新的随机 requestId。

结果：

~~~text
AgentAction taskId ≠ Router requestId ≠ quota operation requestId
~~~

同一次下游 Router 内重试仍安全，因为 identity 在一次 Service 调用内保持不变。

但跨进程恢复或人工重放 AgentAction 时，无法天然用 taskId 找到同一 Router/Quota 操作。

改进：

- AgentAction 执行时调用 QuotaRequestIdentity.legacy(scope, taskId 派生值)；
- 或显式把 executionContext 传给下游；
- AiToolContext.businessId 保存 taskId；
- agent_action_task 保存 ai_request_id、quota_operation_id；
- 重启 reconcile 能关联三张表。

---

# 第二十五部分：跨层失败矩阵

## 71. 失败时到底发生什么

| 失败点 | AgentAction | Router | 配额/结果 |
|---|---|---|---|
| 本地意图分类超时 | 不创建服务端任务 | 未进入 | 不扣费 |
| 服务端动作不在白名单 | prepare 失败 | 未进入 | 不扣费 |
| Scope 不足 | prepare 失败 | 未进入 | 不扣费 |
| 参数非法 | prepare 失败 | 未进入 | 不扣费 |
| 用户不确认 | 保持 PREPARED 后逻辑过期 | 未进入 | 不扣费 |
| confirm 重复 | 返回已有状态，只启动一次 | 视首请求而定 | 不因重复 confirm 重复扣 |
| QUEUED 时取消 | CANCELLED | 未进入 | 不扣费 |
| 已 RUNNING 再取消 | 取消失败 | 继续 | 按业务结果处理 |
| 本地线程池积压 | QUEUED | 尚未进入 | 尚未扣 |
| 全局容量满 | RUNNING 后业务抛错，最终 FAILED | capacity rejected | Router operation 尚未扣 |
| 所有 Gitee slot 不可用 | RUNNING | fallback | 正常扣费流程 |
| Provider 429 | RUNNING | RETRY_WAIT，slot throttle | 同 requestId 不重复扣 |
| Provider 401 | RUNNING | slot invalid，换 Key/fallback | 同 requestId 不重复扣 |
| 最终 Provider 失败 | FAILED | FAILED/TIMEOUT | 业务层应退款 |
| 结果保存失败 | FAILED | FAILED，不再重试 | AiResultRecordService 尝试退款 |
| finish 日志失败 | 可 SUCCEEDED | 结果仍返回 | 结果已保存，日志可能残留 RUNNING |
| 服务重启 | QUEUED/RUNNING → FAILED | 当次进程终止 | 可能需人工 reconcile |
| Router 已保存、外层未落成功时崩溃 | 重启后 FAILED | 结果可能已成功 | 可能已扣费且结果存在 |
| 租约续期失败 | 仍 RUNNING | 操作继续，可能过度并发 | 结果取决于 Provider |

---

# 第二十六部分：高频面试问答

## 72. 为什么要 prepare/confirm，不直接执行

答：

> 模型输出只是非可信的动作建议。OrangeVC 在服务端重新验证设备身份、Scope、动作白名单、参数和业务对象，并把付费或有写副作用的动作保存为 PREPARED。用户确认后才通过条件 SQL 转成 QUEUED。这样把模型决策与真实副作用隔离，也让参数、价格预览、审计和过期策略有明确载体。

## 73. 免费动作为什么也可能需要确认

答：

> 确认保护的不只是费用，还保护副作用。launch_check.project.create 当前费用为 0，但会写数据库，所以仍需确认。只读查询可以直接 SUCCEEDED。

## 74. 怎样保证重复 confirm 不重复执行

答：

> 任务先按账号和设备校验归属，随后用 UPDATE ... WHERE status='PREPARED' AND expires_at>NOW() 做原子状态转换。并发 confirm 中只有一个更新到 1 行，只有它会提交线程池。第二个请求重新读取并返回 QUEUED/RUNNING 等已有状态。

## 75. confirm 幂等是否等于整个业务幂等

答：

> 不等于。confirm 防止同一 agent_action_task 在正常数据库状态下重复入队；下游模型和扣费有自己的 requestId。当前 Agent taskId 没贯通到下游身份，所以跨重启恢复、人工重放和跨表 reconciliation 仍不够完整。

## 76. cancel 如何避免和 worker 冲突

答：

> cancel 只允许 PREPARED/QUEUED，worker 只允许 QUEUED→RUNNING。二者竞争同一个旧状态 QUEUED，谁先成功谁获得执行权；取消先成功则 worker 的 markRunning 更新 0 行并退出，worker 先成功则 cancel 失败。

## 77. 服务重启后怎样恢复

答：

> 当前不会自动续跑。启动时把所有 QUEUED/RUNNING 标为 FAILED，errorCode=SERVER_RESTARTED，提醒用户核对网页结果和配额。这样避免盲目重复副作用，但只能算中断识别，不是持久化工作流恢复。

## 78. AgentAction 是不是工作流引擎

答：

> 不是。它有持久化状态机和异步线程池，但动作分派是硬编码 switch，没有通用 DAG、step checkpoint、分布式 claim、任务级 retry、DLQ 和可恢复补偿。因此更准确叫受控动作执行层。

## 79. AiToolRouter 解决什么问题

答：

> 它统一治理多个 AI 业务调用的结果重放、全局并发、Key 独占、fallback、租约续期、错误分类、自动重试、attempt 日志、指标和结果持久化。业务 Service 仍负责 Prompt、解析、扣费和业务补偿。

## 80. 为什么既要全局 lease 又要 slot lease

答：

> 全局 lease 限制所有 Provider 总调用量，slot lease 保证某个 Gitee Key 不被并发复用。只有 slot 没有总量保护，fallback 可能无限扩张；只有全局没有单 Key 保护，某个 Key 会被多个请求同时压垮。

## 81. 为什么用 Redis Lua

答：

> 获取租约需要把“检查状态、检查数量、写入租约”做成一个原子动作。若用多条普通 Redis 命令，两个实例可能同时看到空闲并一起占用。Lua 在 Redis 单线程执行语义下完成原子判断与写入。

## 82. 租约和锁有什么区别

答：

> 这里更准确叫 lease。它有 TTL，需要续期，即使持有者崩溃也会自动释放。普通无过期锁可能永久死锁；lease 的代价是续期失败或长暂停时可能过期，使两个请求短暂认为自己都拥有资源。

## 83. 401 为什么还重试

答：

> 因为错误可能只属于当前 Key。Router 将当前 Gitee slot 标 INVALID，释放后下一 attempt 可选择其他 Key或 fallback。若是 fallback 自身 401，当前没有独立 Key 状态，默认无限重试会有风险。

## 84. 429 怎样处理

答：

> 解析 Retry-After，把当前 Gitee slot 标 THROTTLED，并用 Provider 指定时间或指数退避等待。下一 attempt 重新选槽。若 Retry-After 缺失，当前 setThrottled 不会建立默认冷却，这是可改进点。

## 85. 重试会不会重复扣费

答：

> Router 可能多次执行 operation，但同一次 Service 调用复用同一个 QuotaRequestIdentity。quota_operation 以 requestId 唯一，后续 consume 会验证账号、scope、requestHash并返回原 charge，不再次扣费。可重试异常也暂不退款，最终失败才补偿。

## 86. 重试时是否占着 Key

答：

> operation 执行期间占用。一次 attempt 抛错后，finally 会先取消续租并释放全局和 slot，外层随后 sleep 退避。因此退避期间不占模型容量，但仍占用请求线程或 Agent worker。

## 87. maxAttempts=0 是什么

答：

> 代码中表示无限重试，不是零次。默认配置因此存在长期占用线程和无限 attempt 日志的风险，生产应设置次数和总 deadline。

## 88. Key 原文会不会进入数据库

答：

> 调用池 Key 从服务器环境读取，日志和管理接口只保存/展示 SHA-256 前 12 位 fingerprint，不写原始 Key。调用异常摘要还会脱敏 Bearer Token，但其他敏感文本仍需统一日志脱敏。

## 89. 结果重放是不是 single-flight

答：

> 不是。它只复用已落库结果。并发首请求都可能先 miss。池开启时 attempt 唯一键可能让其中一个在日志插入处失败，但不会等待 owner 后重放；池关闭时甚至可能都执行。因此还缺 in-flight owner、等待和接管协议。

## 90. 如果调用日志数据库挂了，会不会继续调用模型

答：

> insertLog 在 operation 前，失败会中止并释放租约，所以是 fail-closed。finishLog 在 operation 后且异常被吞掉，结果仍交付，所以结束审计是 best-effort。这防止无开始记录的高成本调用，同时避免完成后因日志故障丢用户结果。

## 91. 这个系统最优先改什么

答：

> 第一优先是把 Agent taskId、Router requestId、quota operation 串成同一业务身份，并增加重启 reconcile。第二是限制无限重试和增加总 deadline/jitter。第三是把本地无界线程池迁成持久化有界队列。这样先解决重复副作用、状态不一致和资源耗尽，再谈动态 Tool Registry。

---

# 第二十七部分：两分钟完整面试回答

## 92. 可直接练习的答案

> OrangeVC 把 Agent 执行治理分成两层。外层 AgentAction 解决用户授权和业务副作用，内层 AiToolRouter 解决模型资源和调用可靠性。
>
> 设备通过一次性绑定码换取独立 Bearer Token，数据库只保存 Token Hash。请求进入服务端后重新校验账号、设备凭据、动作白名单、Scope、参数和业务对象，不信任本地模型给出的摘要。只读动作直接保存为 SUCCEEDED；付费或写操作先把参数冻结到 agent_action_task，状态为 PREPARED，五分钟内由用户确认。确认使用带 status='PREPARED' 的条件 UPDATE，只有一个并发请求能转成 QUEUED 并提交两线程执行器。worker 再用 QUEUED→RUNNING 条件更新与 cancel 竞争，成功后调用具体业务 Service，最后标 SUCCEEDED 或 FAILED。服务重启会把遗留 QUEUED/RUNNING 标为 SERVER_RESTARTED，而不是盲目续跑。
>
> 进入模型调用后，AiToolRouter 先按 requestId、accountId、requestHash 查已保存结果。未命中时先用 Redis Sorted Set 获取全局 lease，再用 Lua 独占一个 Gitee Key slot；所有 slot 忙、禁用、失效或限流时走 DeepSeek fallback。每个 attempt 先写 RUNNING 日志，再每 30 秒续租，执行业务 operation，保存结果后标成功，finally 用 compare-value 释放 slot。
>
> Router 对 401、403、408、409、425、429、5xx 和超时分类重试。401/403 会永久标记当前 Gitee slot 无效，429 按 Retry-After 临时 throttle，其他瞬时错误短暂冷却。每次重试都会释放旧 lease、退避后重新获取。operation 虽可能多次执行，但同一 QuotaRequestIdentity 让扣费按 requestId 幂等，不会重复扣；结果保存失败会退款并停止重试。
>
> 我会明确它当前不是通用 Agent Runtime：动作是硬编码 switch，本地执行器只有两个 daemon 线程且队列无界，没有分布式任务恢复；completed result replay 也不是 single-flight；默认 maxAttempts=0 实际是无限重试。最重要的改进是贯通 Agent taskId、Router requestId 和 quota operation，增加持久化 claim/reconcile，再收紧重试和队列容量。

---

# 第二十八部分：现场推演题

## 93. 双 confirm

题目：A、B 同时确认同一 PREPARED 任务。

答题要点：

- 二者可能都先读到 PREPARED；
- SQL 条件更新只有一个返回 1；
- 只有返回 1 的请求提交 executor；
- 另一个重新读取 QUEUED/RUNNING；
- 业务只启动一次；
- 仍建议增加真实数据库并发集成测试。

## 94. confirm 与 cancel

题目：任务已经 QUEUED，用户同时 cancel，worker 同时 markRunning。

答题要点：

- 都要求旧状态 QUEUED；
- cancel 赢：CANCELLED，worker 更新 0 行退出；
- worker 赢：RUNNING，cancel 更新 0 行并报不能取消；
- 不会同时成为 CANCELLED 和 RUNNING。

## 95. Provider 401

题目：Gitee slot 3 返回 401，maxAttempts=3。

答题要点：

- attempt 日志记录 401/AUTH_REJECTED；
- slot 3 标 INVALID；
- finally 释放；
- RETRY_WAIT；
- 退避；
- 下一 attempt 重新拿全局 lease；
- 尝试其他 Gitee slot，全部不可用则 fallback；
- quota 不重复扣。

## 96. Provider 429 无 Retry-After

答题要点：

- 429 可重试；
- retryDelay 使用指数退避；
- 但 setThrottled 收到 0 后不建冷却键；
- 释放后下一 attempt 可能又选同一槽；
- 应为缺失 Retry-After 设置默认 throttle TTL。

## 97. Router 保存成功，Agent 外层落库前崩溃

答题要点：

- ai_result_record 可能存在；
- quota_operation 可能 CONSUMED；
- agent_action_task 仍 RUNNING；
- 重启后被标 FAILED/SERVER_RESTARTED；
- 不是数据永久丢失，但跨表状态不一致；
- 用 taskId 贯通身份并做 reconcile。

## 98. 无限重试与两线程池

答题要点：

- 两个 Agent worker 都可能被 Router 的 Thread.sleep 重试循环占住；
- lease 在退避时已经释放，所以模型容量没有被占；
- 但新的设备任务只能在无界队列积压；
- 应设置 maxAttempts/deadline，长任务迁到持久化队列。

---

# 第二十九部分：代码阅读地图

## 99. 设备认证和入口

- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\service\AgentDeviceAuthService.java
- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\interceptor\AgentDeviceInterceptor.java
- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\controller\agent\AgentActionController.java
- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\config\WebMvcConfiguration.java
- D:\Orangejd\orange-server\src\main\resources\db\migration\V13__add_agent_device_bridge.sql

必须回答：

1. accountId 从哪里变可信？
2. Token 为什么只存 hash？
3. Scope 在哪一层再次校验？
4. confirm 为什么不能确认别的设备任务？

## 100. AgentAction

- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\service\AgentActionService.java
- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\service\AgentActionSupport.java
- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\service\AgentActionPreparationService.java
- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\service\AgentActionExecutionService.java
- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\service\AgentActionRepository.java
- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\mapper\AgentActionMapper.java

必须回答：

1. 哪些动作同步完成，哪些必须确认？
2. 参数怎样冻结？
3. 重复 confirm 的原子点在哪里？
4. cancel 和 markRunning 怎样竞争？
5. 重启恢复为什么不是自动续跑？
6. price/balance 为什么只是快照？

## 101. MCP 连接器

- D:\Orangejd\tools\OrangeVC-XiaoZhi-Connector\orangevc_xiaozhi_connector\mcp_server.py
- D:\Orangejd\tools\OrangeVC-XiaoZhi-Connector\orangevc_xiaozhi_connector\agent.py
- D:\Orangejd\tools\OrangeVC-XiaoZhi-Connector\orangevc_xiaozhi_connector\service.py
- D:\Orangejd\tools\OrangeVC-XiaoZhi-Connector\orangevc_xiaozhi_connector\conversation.py
- D:\Orangejd\tools\OrangeVC-XiaoZhi-Connector\orangevc_xiaozhi_connector\models.py
- D:\Orangejd\tools\OrangeVC-XiaoZhi-Connector\orangevc_xiaozhi_connector\adapters\orangevc.py

必须回答：

1. 模型分类为什么不是最终授权？
2. MCP confirm 的“用户明确同意”由谁保证？
3. status 怎样自动轮询？
4. trend.profile.generate 为什么不在普通 AgentDecision 白名单？

## 102. AiToolRouter

- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\foundation\aitool\AiToolRouter.java
- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\foundation\aitool\AiToolContext.java
- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\foundation\aitool\AiToolPoolProperties.java
- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\foundation\aitool\AiToolSlotLeaseService.java
- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\foundation\aitool\AiToolRetryPolicy.java
- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\foundation\aitool\AiToolProviderException.java
- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\foundation\aitool\AiToolCallLogMapper.java
- D:\Orangejd\orange-server\src\main\resources\db\migration\V23__add_ai_tool_call_log.sql

必须回答：

1. 为什么 replay 在租约前？
2. 全局 lease 和 slot lease 分别控制什么？
3. fallback 在什么条件触发？
4. attempt 失败后何时释放？
5. 401、429、timeout 的处理差异？
6. maxAttempts=0 到底是什么？
7. 为什么 finishLog 失败不让业务失败？

## 103. 结果和配额

- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\service\AiResultRecordService.java
- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\foundation\aitool\AiResultRecordMapper.java
- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\service\impl\QuotaServiceImpl.java
- D:\Orangejd\orange-server\src\main\java\com\orangejd\server\service\QuotaRequestIdentity.java
- D:\Orangejd\orange-server\src\main\resources\db\migration\V24__add_ai_result_record.sql
- D:\Orangejd\orange-server\src\main\resources\db\migration\V37__harden_quota_ledger.sql

必须回答：

1. Router 重试为什么不重复扣费？
2. 保存失败为什么停止重试？
3. requestId 与 requestHash 分别是什么？
4. ResultRecorder 自身是否会拒绝 hash 冲突？
5. taskId 为什么还需要贯通？

---

# 第三十部分：测试证据

## 104. 本阶段实际运行的后端测试

~~~text
AiToolRetryPolicyTest     2
AiToolRouterTest         11
AgentActionServiceTest   11
合计                     24
Failures                  0
Errors                    0
~~~

已覆盖：

- 池关闭兼容；
- 完成结果重放；
- 全局容量拒绝；
- Gitee slot 使用与释放；
- fallback；
- 日志插入失败释放；
- 401 invalid；
- 429 Retry-After；
- 自动重试直到保存；
- 全局并发硬上限 80；
- Key fingerprint；
- Retry-After 解析；
- 可重试/不可重试判断；
- 只读动作；
- 付费动作只 prepare；
- V2 画像准备；
- 方案前序继承；
- 终态历史删除。

## 105. 本阶段实际运行的连接器测试

使用 Python 标准库 unittest 运行：

~~~text
test_service
test_mcp_integration
test_mcp_polling

Ran 9 tests
OK
~~~

已覆盖：

- 分类超时安全失败；
- 付费动作确认；
- 模拟环境重复 confirm；
- 只读动作；
- remote prepare 转发；
- unsupported 不建任务；
- MCP 工具列表与调用；
- RUNNING 自动轮询；
- 结束后主动返回完成结果。

## 106. 测试没有证明什么

- 真实 MySQL 下的并发 confirm；
- 真实 Redis 多实例 lease；
- 续租故障造成的过度并发；
- JVM 崩溃后的配额 reconcile；
- 高峰无界队列；
- fallback 长期 401/429；
- 无限重试资源占用；
- Agent taskId 与 Router requestId 贯通；
- 真实 Provider 压测承载量。

---

# 第三十一部分：简历描述与诚实边界

## 107. 如果你确实参与了实现

> 参与 OrangeVC Agent 工具执行治理建设：通过独立设备凭据、动作白名单与 Scope 校验建立服务端信任边界，对付费及写操作采用 prepare/confirm 二阶段确认，结合持久化任务状态机和条件 SQL 防止重复确认与取消竞态；构建统一 AiToolRouter，以 Redis 全局/Key 双层租约治理模型并发，支持多 Key 轮询、fallback、租约续期、结果重放、错误分类重试、attempt 审计及幂等扣费与失败退款。

## 108. 如果你主要是阅读、测试和学习

> 深入分析并复现 OrangeVC Agent 工具执行治理链路：梳理设备鉴权、Scope、prepare/confirm 状态机、Redis 租约、Key 调度、fallback、自动重试、结果重放及配额补偿机制；通过后端 24 项和连接器 9 项测试验证关键行为，并识别任务身份未贯通、重启不可恢复、无限重试和本地无界队列等工程边界。

不要写：

- 主导通用 Agent Runtime；
- 实现分布式工作流引擎；
- 实现 exactly-once；
- 实现动态 Agent Skills 注册；
- 支持 80 并发用户且生产验证；
- 实现完整分布式追踪；
- 实现工具沙箱。

---

# 第三十二部分：阶段完成标准

## 109. 不看文档，你应能做到

1. 一句话区分 AgentAction 与 AiToolRouter；
2. 画出设备认证和四个 MCP Tool；
3. 说出 14 个服务端 action 的分组；
4. 解释为什么免费写操作也确认；
5. 画出 PREPARED、QUEUED、RUNNING 和四个终态；
6. 推演双 confirm；
7. 推演 confirm/cancel 竞态；
8. 解释参数冻结；
9. 解释为什么重启恢复只是标失败；
10. 画出 Router 的完整成功 attempt；
11. 区分全局 lease 与 slot lease；
12. 解释 compare-value release；
13. 说清 401、429、timeout 的策略；
14. 解释退避期间释放 lease、但占线程；
15. 解释 maxAttempts=0；
16. 解释 Router 重试为何不重复扣费；
17. 区分 completed replay 与 single-flight；
18. 指出 taskId 和 Router requestId 没贯通；
19. 给出至少 5 个真实局限；
20. 用两分钟完整回答本阶段。

最终记忆锚点：

> AgentAction 把“不可信动作”变成“经用户授权的持久化业务任务”；AiToolRouter 再把其中的模型调用变成“受容量、Key、租约、重试、幂等、审计和补偿治理的 attempt”。OrangeVC 已经有可讨论的 Agent 执行基础设施，但离通用、分布式、可恢复的 Agent Runtime 仍有清晰边界。

