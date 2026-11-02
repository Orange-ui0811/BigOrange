先给你一个总定义：

> OrangeVC 的一次受控 Agent 执行，是“本地意图识别 + 服务端重新鉴权 + 动作白名单与 Scope 校验 + prepare/confirm 二阶段确认 + 持久化任务状态机 + AI 调用资源治理 + 幂等扣费与结果保存”的完整链路。

它不是模型输出一个 Tool Call 后直接执行，也不是通用 Agent 框架自动完成。这个项目里：

- 本地规则或模型只负责把自然语言转换成结构化动作建议。
- `AgentAction` 负责设备权限、参数校验、用户确认和业务任务状态。
- `AiToolRouter` 负责模型调用的结果回放、全局并发、Key 槽位、故障切换、重试和审计。
- 真正的价格、业务对象、执行摘要和结果都由服务端决定。
- 外围还有设备凭据、配额账本、AI 结果记录和服务重启恢复。

最关键的边界是：

> OrangeVC 已经实现了 Agent 执行治理的基础能力，但它不是通用的 Agent Runtime，也不是分布式工作流引擎。

---

# 一、先建立全局视角：执行治理分成两个层级

很多人会把 `AgentAction` 和 `AiToolRouter` 混在一起讲。实际上它们解决的是两类不同问题：

```text
第一层：AgentAction 业务动作治理

自然语言
→ 动作分类
→ 设备认证
→ 动作白名单与 Scope
→ 参数和业务对象校验
→ 是否需要用户确认
→ PREPARED / QUEUED / RUNNING / 终态
→ 调用具体业务 Service

第二层：AiToolRouter 模型调用治理

业务 Service
→ 查询是否已有结果
→ 获取全局并发租约
→ 获取 Gitee Key 槽位
→ 没有可用槽位时走 DeepSeek fallback
→ 记录 attempt
→ 执行模型调用
→ 分类重试
→ 保存结果
→ 释放租约
```

可以把它们理解为：

- `AgentAction` 回答：“用户是否允许执行这个业务动作？”
- `AiToolRouter` 回答：“这个模型请求应该占用哪份资源，失败后怎么办？”

二者的关系是：

```text
AgentAction：PREPARED → QUEUED → RUNNING
                              ↓
                         具体业务 Service
                              ↓
AiToolRouter：结果回放 → 租约 → Key/fallback → attempt → 重试
                              ↓
AgentAction：SUCCEEDED / FAILED
```

---

# 二、AgentAction：模型动作如何变成受控业务任务

## 1. 从 MCP 工具进入 OrangeVC

小智连接器入口在 [mcp_server.py](D:/Orangejd/tools/OrangeVC-XiaoZhi-Connector/orangevc_xiaozhi_connector/mcp_server.py:1)。

它对外提供四个核心 MCP Tool：

- `orangevc_agent_prepare`：理解用户指令并准备动作；
- `orangevc_agent_confirm`：用户明确同意后确认动作；
- `orangevc_agent_status`：查询并等待异步任务；
- `orangevc_agent_cancel`：取消还没有真正开跑的任务。

基本过程：

```text
用户说：
“给项目 12 生成第 3 阶段上线体检”

连接器：
自然语言 → action + parameters

{
  action: "launch_check.stage.generate",
  parameters: {
    projectId: 12,
    stageNo: 3
  },
  summary: "生成第三阶段上线体检"
}

然后调用：
POST /agent/actions/prepare
```

连接器可以使用：

- 确定性规则；
- OpenAI 兼容模型；
- Claude Code 的结构化输出。

但无论使用哪种分类方式，模型结果都只是“动作建议”，不是最终授权。

## 2. 为什么本地模型不能直接执行

本地分类器可能出现：

- 选错动作；
- 生成不存在的项目 ID；
- 把 GitHub Issue 编号当成平台内部 ID；
- 缺少必要参数；
- 被用户输入中的 Prompt Injection 影响；
- 在没有明确确认时执行付费操作；
- 输出一个不在系统能力范围内的动作。

所以 OrangeVC 把信任边界放在服务端：

```text
本地模型输出
      ↓ 不可信
服务端设备认证
      ↓
服务端动作白名单
      ↓
设备 Scope
      ↓
参数长度、类型和业务归属
      ↓
服务端生成价格和执行摘要
```

一个很重要的代码细节是：

> 连接器会提交 summary，但服务端不使用模型提供的 summary 作为最终执行说明，而是根据真实业务数据重新生成 actionSummary 和 spokenMessage。

这样模型不能用伪造摘要隐藏真实动作或伪造费用。

## 3. 设备身份是如何建立的

设备认证主要在：

- [AgentDeviceAuthService.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/AgentDeviceAuthService.java:24)
- [AgentDeviceInterceptor.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/interceptor/AgentDeviceInterceptor.java:13)

绑定过程：

```text
用户在网页生成一次性绑定码
→ 绑定码 8 位，10 分钟有效
→ 设备使用绑定码交换 Token
→ Token 以 ojd_ 开头
→ Token 有效期 90 天
→ 数据库只保存 SHA-256 Hash 和末尾提示
→ 设备以后使用 Bearer Token
```

请求到达 `/agent/**` 后，Interceptor：

1. 读取 `Authorization: Bearer ...`；
2. 对 Token 计算 Hash；
3. 查找有效且未撤销的设备凭据；
4. 把可信 `accountId` 放入 `BaseContext`；
5. 把 `credentialId` 和 `scopes` 放入请求属性；
6. 请求结束后清理上下文。

因此，Controller 不相信客户端自己提交的 accountId，而是使用设备凭据绑定的账号。

## 4. 动作白名单与 Scope

服务端最终动作白名单在 [AgentActionSupport.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/AgentActionSupport.java:5)。

当前支持 14 个动作：

```text
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
```

[AgentActionPreparationService.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/AgentActionPreparationService.java:6) 同时检查：

```text
action 必须属于 ALLOWED_ACTIONS
并且
设备 scopes 必须包含这个 action
```

两者区别：

- 白名单表示平台有没有实现这项动作；
- Scope 表示当前设备有没有资格执行这项动作。

当前实现的边界是：服务端已经有 Scope 校验，但设备绑定时默认发放同一套完整 Scope，暂时不是按设备动态配置的最小权限系统。

## 5. 哪些动作直接完成

以下只读动作在 `prepare` 阶段同步执行，并直接创建 `SUCCEEDED` 任务：

- 查询配额；
- 查询最近语音任务；
- 查询未读通知；
- 查询 GitHub 榜单；
- 查询上线体检项目；
- 查询开源 Issue；
- 查看 Issue 详情。

即使是同步只读动作，也会保存任务记录。这样语音端拥有统一的：

- `taskId`；
- 状态；
- 结果；
- 播报文本；
- 历史记录。

## 6. 哪些动作必须确认

以下动作会创建 `PREPARED` 任务：

- 创建上线体检项目；
- 生成上线体检阶段；
- 生成 Issue 岗位匹配；
- 生成 Issue 贡献指南；
- 生成趋势分析；
- 生成画像式趋势分析；
- 生成方案阶段。

这里有一个非常重要的设计：

> 是否需要确认不只取决于是否收费，也取决于是否有副作用。

例如创建上线体检项目当前费用为 0，但它会写入数据库，所以仍然必须确认。

## 7. prepare 阶段做了什么

`prepare` 的主要步骤是：

1. action 最长 64 字符；
2. 校验服务端白名单；
3. 校验设备 Scope；
4. 要求 parameters 必须是对象；
5. 查询当前配额余额；
6. 按动作校验参数；
7. 重新读取真实项目或 Issue；
8. 检查前置业务条件；
9. 从 PricingService 获取当前价格；
10. 生成服务端 actionSummary 和 spokenMessage；
11. 把参数序列化进任务表；
12. 创建 `SUCCEEDED` 或 `PREPARED`。

例如生成上线体检阶段时，服务端会重新检查：

- projectId 是否有效；
- 这个项目是否属于用户；
- stageNo 是否在 1 到 8；
- 生成第二阶段以后，上一阶段是否已经存在；
- 当前动态价格是多少。

因此不能只依赖模型在 MCP Schema 中做的参数限制。

## 8. 为什么要冻结参数

`PREPARED` 任务会把参数写入 `parameters_json`。

用户确认时只提交 `taskId`，不会再次提交 projectId、stageNo 或其他业务参数。

意义是：

- 用户确认的是刚才预览的那一组参数；
- 模型不能在 confirm 阶段偷偷换参数；
- 重复 confirm 不会注入新值；
- 历史记录能还原准备执行的内容；
- 执行线程只从可信数据库读取参数。

## 9. AgentAction 状态机

任务表定义在 [V13__add_agent_device_bridge.sql](D:/Orangejd/orange-server/src/main/resources/db/migration/V13__add_agent_device_bridge.sql:35)。

状态包括：

```text
PREPARED
已校验并冻结参数，等待确认

QUEUED
用户已经确认，等待工作线程执行

RUNNING
工作线程已经开始调用业务服务

SUCCEEDED
执行成功并保存结果

FAILED
执行异常或服务重启中断

CANCELLED
用户在 PREPARED/QUEUED 阶段取消

EXPIRED
超过确认时间
```

完整状态图：

```text
只读动作：
prepare ─────────────────────→ SUCCEEDED

写入或付费动作：
prepare → PREPARED
             │
             ├─ confirm → QUEUED → RUNNING → SUCCEEDED
             │                         └────→ FAILED
             │
             ├─ cancel ───────────────→ CANCELLED
             │
             └─ 5 分钟未确认 ─────────→ EXPIRED

QUEUED：
├─ worker 先抢到 → RUNNING
└─ cancel 先抢到 → CANCELLED
```

## 10. 重复 confirm 为什么不会重复执行

确认逻辑在 [AgentActionExecutionService.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/AgentActionExecutionService.java:7)。

关键 SQL 在 [AgentActionMapper.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/mapper/AgentActionMapper.java:84)：

```sql
UPDATE agent_action_task
SET status = 'QUEUED'
WHERE id = #{id}
  AND status = 'PREPARED'
  AND expires_at > NOW()
```

假设两个请求同时确认：

```text
请求 A：读取到 PREPARED
请求 B：读取到 PREPARED

请求 A：
PREPARED → QUEUED，更新 1 行

请求 B：
WHERE status = PREPARED 已不成立，更新 0 行
```

只有更新 1 行的请求才会把任务提交到线程池。

所以真正防止并发重复确认的不是 Java 中的 `if`，而是：

> 带旧状态条件的数据库原子更新。

## 11. cancel 和 worker 竞争时会怎样

取消 SQL 只允许：

```text
PREPARED / QUEUED → CANCELLED
```

worker 启动 SQL 只允许：

```text
QUEUED → RUNNING
```

如果任务已经 QUEUED：

- cancel 先成功：状态变成 CANCELLED，worker 的 `markRunning` 更新 0 行，直接退出；
- worker 先成功：状态变成 RUNNING，cancel 更新 0 行，返回不能取消。

所以不会出现同一任务同时处于 CANCELLED 和 RUNNING。

但当前“取消”只是在任务开跑前阻止执行，不会中断已经发出的模型 HTTP 请求。

## 12. 异步执行和重启恢复

异步执行在 [AgentActionExecutionService.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/AgentActionExecutionService.java:23)。

当前使用：

```text
固定 2 个线程
线程名 agent-action-worker
daemon 线程
默认无界等待队列
```

worker 会：

1. `QUEUED → RUNNING`；
2. 设置当前 accountId；
3. 重新读取任务；
4. 解析冻结参数；
5. 调用具体业务 Service；
6. 成功后写 resultJson 和最新余额；
7. `RUNNING → SUCCEEDED`；
8. 异常时转为 `FAILED`；
9. 最后清理线程上下文。

应用启动时，[AgentActionRepository.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/AgentActionRepository.java:7) 会把遗留的 `QUEUED/RUNNING` 标成：

```text
FAILED
errorCode = SERVER_RESTARTED
```

它不会自动续跑，而是提示用户检查网页 AI 结果和配额记录。

准确描述应该是：

> 当前实现了重启中断识别，没有实现持久化任务恢复。

---

# 三、AiToolRouter：模型调用如何被统一治理

## 1. Router 的职责

核心入口在 [AiToolRouter.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolRouter.java:49)。

它负责：

- 结果回放；
- 全局并发；
- Gitee Key 独占；
- DeepSeek fallback；
- 租约续期；
- attempt 日志；
- 错误分类；
- 自动重试；
- Key 失效与限流隔离；
- 结果持久化；
- 指标统计；
- finally 释放资源。

它不负责：

- 理解用户自然语言；
- 选择业务 action；
- 构造 Prompt；
- 判断业务结果内容；
- 决定价格；
- 直接实现配额账本。

## 2. AiToolContext 包含什么

[AiToolContext.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolContext.java:3) 携带：

- `purpose`；
- `requestId`；
- `accountId`；
- `businessType`；
- `businessId`；
- `legacyConfigType`；
- `requestHash`。

作用：

- requestId：标识同一次模型业务操作；
- requestHash：标识本次请求的参数内容；
- accountId：结果属于哪个用户；
- businessType/businessId：用于记录和展示；
- purpose：用于审计；
- legacyConfigType：调用池关闭时选择旧配置。

## 3. 第一步为什么先查结果

Router 首先调用：

```java
Object existing = resultRecorder.findExisting(context);
if (existing != null) {
    return (T) existing;
}
```

查询条件是：

```text
requestId + accountId + requestHash
```

如果已经有结果：

- 不占全局容量；
- 不占 Key；
- 不再调用 Provider；
- 不重复保存；
- 直接返回之前的结果。

这叫“已完成结果回放”。

它和 single-flight 不同：

- 结果回放只能复用已经保存完成的结果；
- single-flight 还要协调正在执行中的相同请求，让后来的请求等待第一个请求。

当前没有完整的 in-flight owner/waiter 协议。

## 4. 调用池关闭时发生什么

如果 `orange.ai-tool-pool.enabled=false`：

```text
使用 legacyConfigType 获取旧模型配置
→ 直接执行业务 operation
→ 保存结果
```

此时不会：

- 获取 Redis 租约；
- 写 ai_tool_call_log；
- 使用 Gitee Key 槽位；
- 记录 Router 调用指标。

这是为了兼容旧运行方式。

## 5. 全局并发租约

全局租约在 [AiToolSlotLeaseService.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolSlotLeaseService.java:22)。

Redis 使用 Sorted Set：

```text
Key：
orangevc:ai-tool:global:leases

member：
随机 leaseId

score：
租约过期时间
```

获取时 Lua 脚本会原子执行：

1. 删除已经过期的租约；
2. 查询当前租约数量；
3. 达到上限则返回失败；
4. 未达到则写入新 leaseId。

配置范围在 [AiToolPoolProperties.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolPoolProperties.java:29)：

```text
globalMaxConcurrency：
默认 50，硬限制 1 到 80

acquireWaitMillis：
默认 2000 ms，最大 10000 ms

leaseMillis：
默认 330000 ms，限制 60000 到 600000 ms
```

如果一次等待窗口内取不到全局容量：

- 增加 `orange.ai.tool.capacity.rejected` 指标；
- 抛出“AI 服务繁忙”；
- 外层 RetryPolicy 会把该错误视为可重试；
- 如果允许自动重试，会退避后再次尝试。

## 6. 为什么还需要 Key 槽位

全局租约限制系统总调用量，但不能防止同一个 API Key 被并发使用。

每个 Gitee Key 对应一个 slot：

```text
orangevc:ai-tool:slot:1
orangevc:ai-tool:slot:2
...
最多 10 个
```

获取 slot 的 Redis Lua 同时检查：

- 这个 slot 是否 BUSY；
- 是否被管理员 DISABLED；
- 是否因 401/403 被标 INVALID；
- 是否因 429 被标 THROTTLED。

全部通过后，使用 `PSETEX` 写入带 TTL 的租约。

因此：

> 全局租约限制全部模型调用，slot 租约保护单个 Gitee Key。

## 7. Key 如何轮询

Slot 服务使用一个本地 `AtomicInteger cursor` 选择本次扫描起点，然后循环所有配置 Key。

可以理解为：

```text
请求 1 从 slot 1 开始
请求 2 从 slot 2 开始
请求 3 从 slot 3 开始
...
```

本地 cursor 改善本实例的分布，Redis Lua 保证多实例之间的最终独占。

当前不是根据：

- RPM；
- TPM；
- 历史延迟；
- 剩余额度；
- 成功率；

做智能调度，而是 round-robin 起点加可用性检查。

## 8. 什么时候使用 DeepSeek fallback

如果所有 Gitee slot 都不可获取：

```text
slot == null
→ configService.current(TOOL_FALLBACK)
→ provider = DEEPSEEK
```

所有 slot 不可获取可能是：

- 全部 BUSY；
- 被管理员禁用；
- 401/403 后 INVALID；
- 429 后 THROTTLED。

fallback 也受全局并发上限约束，但没有自己的独立 slot 和 Key 健康状态。

所以如果 fallback 自身持续 401 或 429，当前无法像 Gitee slot 那样隔离具体 Key。

## 9. 为什么使用租约而不是永久锁

租约带 TTL。

如果进程崩溃、finally 没执行：

- 全局租约会随 score 过期被清理；
- slot Key 会因 TTL 自动删除；
- 不会永久占用容量。

Router 每 30 秒续约一次。

默认租约 330 秒，因此长模型调用期间会不断延长到期时间。

## 10. compare-value 释放

slot 释放不是直接 `DEL`，而是：

```text
只有 Redis 当前值仍然等于我的 lease value
才能删除
```

原因：

1. 请求 A 的租约过期；
2. 请求 B 获得同一 slot；
3. A 很晚才执行 finally；
4. 如果 A 直接 DEL，会误删 B 的租约；
5. compare-value 可以证明当前锁仍属于 A。

这是 Redis 租约中非常重要的所有权保护。

## 11. attempt 日志记录什么

日志表定义在 [V23__add_ai_tool_call_log.sql](D:/Orangejd/orange-server/src/main/resources/db/migration/V23__add_ai_tool_call_log.sql:1)。

每次 attempt 记录：

- requestId；
- attemptNo；
- accountId；
- businessType；
- businessId；
- purpose；
- provider；
- slotNo；
- Key fingerprint；
- model；
- status；
- HTTP status；
- errorCode；
- errorSummary；
- duration。

状态包括：

```text
RUNNING
RETRY_WAIT
SUCCEEDED
TIMEOUT
FAILED
```

日志不会保存：

- Prompt；
- Response；
- 原始 API Key。

Key fingerprint 是 API Key 的 SHA-256 前 12 位，用于定位哪个槽位有问题，同时避免暴露 Key 原文。

## 12. 一次成功调用的顺序

```text
查询已有结果
→ 获取全局租约
→ 获取 Gitee slot 或 fallback
→ 插入 RUNNING attempt 日志
→ 启动 30 秒续租
→ operation.run(config)
→ 保存 AI 结果
→ attempt 标记 SUCCEEDED
→ success metric +1
→ finally 取消续租
→ 释放全局和 slot 租约
```

这里有一个重要顺序：

> 先保存业务结果，再把 attempt 标记为成功。

因为如果模型已经生成，但结果无法持久化，就不能把这次调用视为完整成功。

## 13. 调用日志失败的两种处理

插入日志发生在模型调用前。

如果 `insertLog` 失败：

- 不执行模型；
- 抛出异常；
- finally 释放租约。

这是 fail-closed：没有开始审计，就不启动高成本调用。

但模型完成后的 `finishLog` 失败时：

- 只记录服务器错误日志；
- 不让已完成的业务结果失败；
- attempt 记录可能暂时停留在 RUNNING。

这是 best-effort：不能因为结束日志故障丢弃用户已经得到的结果。

---

# 四、一次完整 Agent 付费请求的执行过程

以用户说：

```text
“为 Issue 27 生成贡献指南”
```

为例。

## 第 0 步：本地意图识别

连接器把语音转换为：

```json
{
  "action": "open_source.issue.guide",
  "parameters": {
    "issueId": 27
  },
  "summary": "为 Issue 27 生成贡献指南"
}
```

本地分类结果仍然不可信。

## 第 1 步：设备认证

连接器携带设备 Bearer Token 调用：

```http
POST /agent/actions/prepare
```

`AgentDeviceInterceptor` 验证 Token Hash，恢复：

- accountId；
- credentialId；
- scopes。

## 第 2 步：服务端重新授权

`AgentActionPreparationService` 检查：

- action 在服务端白名单；
- scopes 包含 `open_source.issue.guide`；
- parameters 是对象；
- issueId 是数字；
- Issue 27 在数据库中真实存在。

连接器提供的 summary 不作为最终可信摘要。

## 第 3 步：查询价格并创建 PREPARED

服务端通过 PricingService 查询贡献指南当前价格，然后写入任务：

```text
status = PREPARED
parametersJson = {"issueId":27}
quotaCost = 当前价格
balanceSnapshot = 当前余额
expiresAt = 当前时间 + 5 分钟
```

返回：

- taskId；
- confirmationId；
- 预计消耗；
- 当前余额；
- spokenMessage；
- needsConfirmation=true。

这里还没有扣费，也没有调用 AI。

## 第 4 步：用户明确确认

只有用户明确同意后，MCP 才应该调用：

```http
POST /agent/actions/{taskId}/confirm
```

服务端不能从声音本身证明“人类真的同意了”，它能证明的是：

- confirm 来自同一账号的有效设备凭据；
- confirmationId 属于这台设备；
- 任务仍是 PREPARED；
- 任务没有过期。

因此这是“协议和状态层的人在回路”，不是密码学意义上的人类意图证明。

## 第 5 步：PREPARED → QUEUED

数据库条件更新成功后，任务提交到本地两线程池。

重复 confirm 不会重复提交。

## 第 6 步：QUEUED → RUNNING

worker 再次做条件更新。

如果用户已经在 QUEUED 时抢先取消，更新失败，worker 退出。

如果 worker 先进入 RUNNING，之后不再允许取消。

## 第 7 步：读取冻结参数

worker 从 `agent_action_task.parameters_json` 读取 issueId=27，而不是相信 confirm 请求重新提交的数据。

然后调用：

```text
OpenSourceContributionService.generateGuide(accountId, 27)
```

## 第 8 步：生成下游请求身份

业务 Service 创建 `QuotaRequestIdentity`：

```text
requestHash = SHA-256(规范化参数)
requestId = 当前业务调用身份
```

注意：

> AgentAction 的 taskId 当前没有直接传成下游 Router requestId。

worker 线程没有 Servlet 请求中的 Idempotency-Key，因此下游通常生成新的随机 requestId。

## 第 9 步：AiToolRouter 查询结果

Router 按：

```text
requestId + accountId + requestHash
```

查询 `ai_result_record`。

有结果就回放，没有结果才继续。

## 第 10 步：获取模型资源

Router：

1. 获取全局 Redis 租约；
2. 尝试获取 Gitee slot；
3. 没有可用 slot 时使用 DeepSeek fallback；
4. 写入 attempt 日志；
5. 启动续租。

## 第 11 步：幂等扣费

业务 operation 内调用 `quotaService.consume`。

底层 [QuotaServiceImpl.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/impl/QuotaServiceImpl.java:34) 会：

- 锁定账户；
- 按 requestId 查询 quota_operation；
- 已存在时验证 accountId、Scope 和 requestHash；
- 没有时原子扣减；
- 保存 operation 和 ledger。

如果 Router 自动重试，后续 attempt 复用同一个 `QuotaRequestIdentity`，不会重复扣费。

## 第 12 步：调用模型

业务 Service 使用 Router 选择的 `AiRuntimeConfig`：

- endpoint；
- apiKey；
- model；
- provider。

然后拼装 Prompt、调用模型、解析业务结果。

## 第 13 步：失败时分类重试

[AiToolRetryPolicy.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolRetryPolicy.java:8) 将以下错误视为可重试：

```text
HTTP 401 / 403
HTTP 408 / 409 / 425 / 429
HTTP 5xx
SocketTimeoutException
HttpTimeoutException
以及部分明确表示 AI 暂时失败的业务错误
```

处理方式：

- 401/403：Gitee slot 标记 INVALID；
- 429：根据 Retry-After 标记 THROTTLED；
- 其他瞬时错误：当前 slot 临时冷却；
- 然后 finally 释放租约；
- 外层等待退避时间；
- 下一 attempt 重新获取全局租约和 slot。

所以：

> 重试退避期间不会继续占用模型租约，但会占用当前 HTTP 请求线程或 Agent worker 线程。

## 第 14 步：结果持久化

成功结果进入 [AiResultRecordService.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/AiResultRecordService.java:61)。

它会：

- 序列化结果；
- 检查最大长度；
- 保存 requestId、requestHash、业务类型和结果；
- 支持后续回放；
- 结果保存失败时尝试退款并抛错。

## 第 15 步：Router 完成

Router：

- attempt 标记 SUCCEEDED；
- 增加成功指标；
- 取消续租任务；
- 释放全局和 slot 租约；
- 把结果返回业务 Service。

## 第 16 步：AgentAction 完成

worker：

- 获取最新余额；
- 写入 resultJson；
- `RUNNING → SUCCEEDED`；
- 生成适合语音播报的 spokenMessage。

连接器的 `orangevc_agent_status` 会继续轮询，直到任务进入终态，并主动向用户播报完成结果。

---

# 五、自动重试必须讲清的细节

## 1. 重试状态

一次请求可能产生：

```text
attempt 1：RUNNING → RETRY_WAIT
attempt 2：RUNNING → RETRY_WAIT
attempt 3：RUNNING → SUCCEEDED
```

外层 AgentAction 在这个过程中通常一直保持 RUNNING。

## 2. 指数退避

如果 Provider 没有提供 Retry-After：

```text
delay = initialDelay × 2^(attemptNo - 1)
```

默认：

```text
2 秒 → 4 秒 → 8 秒 → 16 秒 → 30 秒 → 30 秒……
```

最大延迟默认 30 秒。

当前没有 jitter。多个请求同时失败时，可能在同一时刻醒来，再次形成流量尖峰。

## 3. Retry-After

如果 429 返回 Retry-After：

- 可以是秒数；
- 也可以是 RFC 1123 日期；
- Router 转成毫秒；
- 重试等待时间受最大延迟限制；
- Gitee slot 的 THROTTLED 时间使用 Provider 给出的值。

如果 429 没有 Retry-After，当前不会为 slot 建立默认 THROTTLED TTL，下一次可能很快重新拿到同一个 Key。

## 4. maxAttempts=0 的含义

配置默认：

```text
auto-retry-max-attempts = 0
```

代码中的含义是：

> 不限制最大重试次数。

不是“不重试”。

这会带来：

- Provider 长期异常时请求一直运行；
- 两个 Agent worker 都可能被占住；
- 后续任务在无界队列中积压；
- attempt 日志持续增长；
- fallback 持续失败时无法自动收口。

## 5. 为什么重试不会重复扣费

Router 的每个 attempt 会重新执行 operation，但同一次 `execute` 调用复用相同的：

- requestId；
- requestHash；
- QuotaRequestIdentity。

配额层发现相同 operation 已经是 `CONSUMED` 时，会返回原来的扣费记录，不再扣一次。

因此当前语义更接近：

```text
模型 operation：至少执行一次
配额扣费：同 requestId 幂等一次
```

## 6. 为什么可重试失败不能立刻退款

如果 attempt 1 超时就退款：

- attempt 2 继续使用同一 requestId；
- 配额层会发现该 operation 已经 REFUNDED；
- 后续重试无法继续。

所以业务 Service 会判断异常是否由 Router 管理重试：

- 可重试：暂不退款；
- 最终不可重试：退款；
- 结果保存失败：由 AiResultRecordService 退款并停止重试。

---

# 六、失败场景如何处理

| 失败位置 | 当前行为 |
|---|---|
| 本地动作分类超时 | 返回安全失败，不创建服务端任务，不扣费 |
| action 不在服务端白名单 | prepare 拒绝 |
| 设备 Scope 不足 | prepare 拒绝 |
| 参数或业务归属错误 | prepare 拒绝 |
| 用户不确认 | 不执行，超过 5 分钟后逻辑过期 |
| 重复 confirm | 返回已有状态，只有一个请求能入队 |
| QUEUED 时取消 | CANCELLED，worker 无法进入 RUNNING |
| RUNNING 后取消 | 拒绝取消，业务继续 |
| 本地线程池积压 | 任务保持 QUEUED，等待两个 worker |
| 调用池未配置完整 | Router 失败，不调用模型 |
| 全局容量满 | 一次获取窗口失败，可由 Router 继续退避重试 |
| 所有 Gitee Key 不可用 | 使用 DeepSeek fallback |
| Gitee Key 401/403 | 对应 slot 标记 INVALID |
| Gitee Key 429 | 对应 slot 按 Retry-After 标记 THROTTLED |
| 模型超时或 5xx | attempt 进入 RETRY_WAIT |
| 结果保存失败 | 尝试退款，Router 不再重试 |
| attempt 开始日志失败 | 不调用模型，释放租约 |
| attempt 结束日志失败 | 业务结果仍返回，日志可能停留 RUNNING |
| 租约续期失败 | operation 继续，但租约可能过期并造成实际并发超限 |
| 服务重启 | QUEUED/RUNNING 任务标 FAILED/SERVER_RESTARTED |
| Router 已成功但外层未落库时崩溃 | AI 结果和扣费可能成功，AgentAction 却在重启后显示 FAILED |

这里体现了三个原则：

1. **模型不能直接执行副作用**：动作必须经过服务端权限与确认。
2. **并发控制必须有失效恢复**：租约依靠 TTL 防止永久占用。
3. **模型可以重试，副作用必须幂等**：配额和结果不能因为 attempt 增加而重复。

---

# 七、这个实现的优点

面试可以重点讲：

- 本地模型决策与服务端真实执行隔离；
- 独立设备凭据，不直接复用用户网页登录 Token；
- API Token 原文不落库；
- 动作白名单和设备 Scope 双重校验；
- 服务端重新校验参数和业务对象；
- 只读动作、写动作、付费动作分级；
- prepare/confirm 二阶段确认；
- 参数冻结；
- 条件 SQL 防止重复 confirm；
- cancel 与 worker 通过旧状态条件解决竞态；
- 任务状态和语音结果可查询；
- 全局租约和 Key slot 两级并发控制；
- Key 轮询和 DeepSeek fallback；
- 401 无效 Key 隔离；
- 429 Retry-After；
- 指数退避；
- attempt 审计和指标；
- 结果回放；
- 幂等扣费；
- 结果保存失败退款；
- finally 释放和 TTL 自愈。

---

# 八、需要主动承认的不足

面试被问“这套执行治理还能怎么改”时，可以回答：

1. **不是通用 Agent Runtime**

   动作列表和执行分派主要是硬编码 switch，没有动态 Tool Registry、通用 Handler、DAG、模型自主规划和 Agent Skills 注册。

2. **AgentAction 不是分布式工作流引擎**

   当前只有 MySQL 状态和本地两线程池，没有分布式 claim、任务租约、持久化消息队列、自动任务重试和 DLQ。

3. **任务队列无界**

   固定线程池默认使用无界队列。高峰时任务可能长时间停留在 QUEUED，没有明确背压、排队长度和预计等待时间。

4. **重启不会自动恢复**

   应用启动只把 QUEUED/RUNNING 标为 SERVER_RESTARTED，不会检查下游结果后续跑或补标成功。

5. **Agent taskId 没有贯通下游身份**

   AgentAction 的 taskId、Router requestId 和 quota operation requestId 是不同身份。跨重启 reconciliation 和人工重放不够方便。

6. **价格和余额只是快照**

   prepare 保存 quotaCost 和 balanceSnapshot，但没有预占额度，也没有把 priceVersion 固定传给下游。确认前价格或余额变化后，实际执行可能不同或失败。

7. **已完成结果回放不是 single-flight**

   两个并发首请求都可能先 miss。调用池开启时，其中一个可能在 attempt 唯一键处失败；调用池关闭时可能都执行。当前没有 owner/waiter 协调。

8. **默认无限重试**

   maxAttempts=0 实际表示无限，Provider 长期故障可能一直占用请求线程或 Agent worker。

9. **没有随机抖动和完整熔断**

   指数退避没有 jitter；fallback 没有独立 Key 健康状态；Redis 租约续期失败后 operation 仍继续。

10. **Scope 机制还比较粗**

    有服务端 Scope 校验，但设备默认获得同一套完整 Scope，尚未形成按设备最小权限配置。

11. **运行中不能真正取消**

    cancel 只能阻止 PREPARED/QUEUED，不能取消已经发送给 Provider 的请求。

12. **测试覆盖还有空白**

    当前测试验证了准备、回放、租约释放、fallback 和重试等行为，但没有真实 MySQL 的双 confirm 并发集成测试，也没有多实例 Redis lease 和崩溃恢复测试。

回答不足时可以说：

> 当前优先解决了不可信模型动作的确认隔离，以及多 Key 模型调用的容量、重试和扣费安全。如果继续迭代，我会先贯通 Agent taskId、Router requestId 和 quota operation，增加持久化任务 claim 与重启 reconciliation，再收紧无限重试和无界队列。

---

# 九、高频面试问题与回答要点

## 1. 为什么 Agent Tool 不能直接执行

> 模型输出具有不确定性，也可能受 Prompt Injection 影响。OrangeVC 让模型只提出 action 和 parameters，服务端重新验证设备身份、动作白名单、Scope、参数、业务归属和价格。付费或写操作还必须经过 prepare/confirm，避免模型的不确定性直接变成真实副作用。

## 2. prepare/confirm 解决了什么问题

- 用户能看到动作和预计费用；
- 参数在确认前被持久化冻结；
- 未确认时不调用模型、不扣费；
- 写操作即使免费也需要确认；
- 确认可以过期；
- 任务状态可审计；
- 重复确认不会重复入队。

## 3. 如何防止重复 confirm

> 使用数据库条件更新 PREPARED→QUEUED。并发请求中只有一个能更新到 1 行，只有它会提交线程池。Java 状态判断改善返回体验，真正的并发保护来自 SQL 中的旧状态条件。

## 4. cancel 和 worker 同时执行会怎样

> 两者都要求旧状态是 QUEUED。cancel 先成功，worker 的 markRunning 更新 0 行并退出；worker 先成功，任务变 RUNNING，cancel 更新 0 行并失败。因此不会出现取消和执行同时成立。

## 5. 为什么免费写操作也要确认

> Human-in-the-loop 保护的不只是费用，还有不可逆或可见的副作用。创建项目虽然当前 cost=0，但会修改业务数据，所以仍然进入 PREPARED。

## 6. AgentAction 是不是 exactly-once

> 不是。它通过状态条件避免同一任务在正常运行时重复启动，但跨进程崩溃后仍可能出现下游已成功、外层任务未标成功的窗口。准确说是状态机约束下的防重复启动，不是端到端 exactly-once。

## 7. AiToolRouter 为什么需要两级租约

> 全局 lease 控制所有 Provider 总并发，slot lease 控制某个 Gitee Key 的独占。只做全局限制会让单 Key 被并发压垮；只做 Key 限制又无法限制 fallback 和系统总资源。

## 8. 为什么使用 Redis Lua

> 获取租约需要把检查过期、判断容量、检查 slot 状态和写入租约做成原子操作。多条普通 Redis 命令之间会产生竞态，而 Lua 可以在 Redis 内一次完成判断和写入。

## 9. 为什么使用 TTL 和续租

> TTL 保证进程崩溃后租约能够自动释放；续租保证长模型请求不会在正常运行时过期。代价是续租失败时租约可能提前失效，因此 lease 不是绝对互斥，需要监控和合理的超时预算。

## 10. 401 为什么还会重试

> 401/403 可能只说明当前 Gitee Key 已失效。Router 把这个 slot 标记为 INVALID，释放后下一 attempt 可以选择其他 Key 或 fallback。因此重试的目标是换资源，不是反复使用同一个错误 Key。

## 11. 429 如何处理

> Router 解析 Retry-After，把当前 Gitee slot 标记为 THROTTLED，并在退避后重新选 Key。Retry-After 可以是秒数或日期。当前缺口是 Provider 没返回 Retry-After 时没有默认 slot 冷却时间。

## 12. 自动重试会不会重复扣费

> 不会重复扣同一个 requestId。不同 attempt 复用同一个 QuotaRequestIdentity，quota_operation 已存在时会验证账号、Scope 和 requestHash，然后返回原消费记录。可重试失败不会立刻退款，最终失败或结果保存失败才补偿。

## 13. 重试等待时会不会占着 Key

> 不会。executeAttempt 的 finally 会先释放全局和 slot lease，外层再 sleep 退避。但原同步请求线程或 Agent worker 仍然被占用，所以无限重试依然会耗尽应用执行能力。

## 14. 结果回放是不是 single-flight

> 不是。它只复用已经落库的成功结果，不能让并发中的第二个相同请求等待第一个完成。真正 single-flight 需要 requestId owner、心跳、等待、超时接管和结果通知。

## 15. 调用日志为什么不保存 Prompt 和响应

> attempt 日志主要用于容量、Provider、Key 槽和错误诊断，避免把用户内容与敏感 Key 写入运行审计。完整业务结果保存在用户拥有的 ai_result_record 中，Key 只记录 SHA-256 fingerprint。

## 16. 服务重启后如何恢复

> 当前启动时把遗留 QUEUED/RUNNING 标为 FAILED，并提示用户检查结果和配额，不会自动续跑。这样避免盲目重复副作用，但仍缺少跨表 reconciliation。更完善的做法是用 taskId 贯通 AI 结果和配额操作，重启后根据下游事实补标成功、退款或进入人工审核。

## 17. 为什么不能说支持 80 个并发用户

> 80 只是 globalMaxConcurrency 的代码硬上限，不是压测结论。真实吞吐还受 Key 限额、模型延迟、fallback 能力、HTTP 连接、数据库、Redis 和重试率影响。应通过 P95 延迟、成功率、RPM/TPM 和资源使用压测后再声称容量。

## 18. 这算 Agent Skills 吗

> 当前更接近固定的受控 Tool 能力集合。动作有白名单、参数约束、权限、确认和执行逻辑，但没有独立 Skill 描述、动态注册、版本管理和可组合工作流。它是封装 Agent Skills 的良好工程基础，不能直接包装成成熟 Skills 平台。

---

# 十、面试中的完整回答模板

OrangeVC 中我重点分析的是一套两层 Agent 执行治理。第一层 AgentAction 负责把不可信的自然语言动作变成经过服务端授权的业务任务，第二层 AiToolRouter 负责治理任务内部真正的模型调用。

设备侧先通过一次性绑定码换取独立 Bearer Token，数据库只保存 Token Hash。自然语言可以由规则或模型转换成 action 和 parameters，但服务端不会直接信任这份结果，而是重新校验设备身份、动作白名单、Scope、参数类型、长度、业务对象归属和当前价格。只读动作可以直接完成；付费或有写副作用的动作会先把参数冻结到 agent_action_task，状态设为 PREPARED，并在五分钟内等待用户确认。

确认时只提交 taskId。服务端使用带旧状态条件的 SQL 将 PREPARED 原子更新为 QUEUED，所以并发重复确认中只有一个请求能够提交线程池。worker 再使用 QUEUED→RUNNING 的条件更新与 cancel 竞争：取消先成功则业务不执行，worker 先成功则不再允许取消。成功后任务保存 resultJson 并进入 SUCCEEDED，异常进入 FAILED。当前使用本地两线程池；服务重启时会把遗留 QUEUED/RUNNING 标为 SERVER_RESTARTED，而不是自动续跑。

业务 Service 需要调用大模型时进入 AiToolRouter。Router 首先按 requestId、accountId 和 requestHash 查询已有结果，命中后直接回放。没有结果时，先通过 Redis Sorted Set 获取全局并发 lease，再通过 Lua 独占一个 Gitee Key slot；所有 slot 忙、禁用、失效或限流时使用 DeepSeek fallback。每个 attempt 都会记录 Provider、slot、Key fingerprint、状态和耗时，长调用每 30 秒续租，finally 使用 compare-value 释放 slot，防止旧请求误删新租约。

对 401/403，Router 会把当前 Gitee Key 标为 INVALID，再尝试其他 Key；对 429 按 Retry-After 临时 THROTTLED；对超时和 5xx 使用指数退避。每个 attempt 都会释放旧 lease，退避后重新获取资源。虽然 operation 可能执行多次，但所有 attempt 复用同一个 QuotaRequestIdentity，配额层按 requestId 幂等，因此不会重复扣费。成功结果先持久化，再把 attempt 标为 SUCCEEDED；结果保存失败会尝试退款并停止重试。

这套实现的价值是把用户确认、业务状态、模型容量、Key 健康、重试、扣费和结果审计串成了一条可运行链路。当前不足是动作和 Handler 仍然硬编码，本地线程池队列无界，重启后不能自动恢复，Agent taskId 没有贯通 Router 和配额身份，已完成结果回放也不是真正 single-flight，而且 maxAttempts=0 实际表示无限重试。

如果继续迭代，我会先用 Agent taskId 贯通 agent_action_task、ai_result_record 和 quota_operation，增加持久化 claim、心跳和重启 reconciliation；然后给执行队列加容量、超时和 DLQ，给 Router 设置有限重试、总 deadline、jitter 和 fallback 熔断；最后再把硬编码动作演进成带 Schema、权限、确认策略和版本信息的可注册 Tool/Skill Handler。

真正掌握的标准是：你能不看代码画出 AgentAction 状态图和 AiToolRouter 两级租约图，能推演双 confirm、confirm/cancel、401、429、无限重试和服务崩溃，能说明哪些行为由测试证明、哪些只是代码不变量，也能明确解释它为什么是 Agent 执行治理基础而不是完整 Agent Runtime。做到这些，AI Agent 工程岗位关于工具调用、安全执行、并发治理、幂等和失败恢复的问题就有足够深度。

