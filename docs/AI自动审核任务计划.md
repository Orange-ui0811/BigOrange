# AI 自动审核 — 可执行任务计划

> 模型：DeepSeek V4 Pro（多模态） | 通知：飞书 Webhook | 模式：全局持久开关 + 新提交自动触发

---

## 0. 架构

```
用户提交 → PENDING_REVIEW
    │
    ├─ Redis ai-audit:enabled = true?
    │   ├─ 是 → 异步触发 AI 审核
    │   └─ 否 → 等人工
    │
    ▼
AiAuditService（异步线程池，最多 2 并发）
    ├─ Step 1: 获取作品详情（标题/简介/详情/封面图公网URL/演示链接）
    ├─ Step 2: 组装多模态 prompt
    ├─ Step 3: POST DeepSeek V4 Pro（不设超时）
    ├─ Step 4: 图片报错 → 自动去图重试 → 飞书通知降级
    ├─ Step 5: 解析 {action, reason}
    ├─ Step 6: 调用 AuditServiceImpl 执行审核（auditorId=0）
    └─ Step 7: POST 飞书 Webhook 推送结果
```

---

## 1. 任务拆解总览

```
Step 1: AiAuditService（核心）        ██████  约 1.5 小时
Step 2: 自动触发机制                    ████    约 1 小时
Step 3: 飞书通知                        ██      约 30 分钟
Step 4: 前端控制栏                       ███     约 45 分钟
Step 5: 配置 + 测试                      ██      约 30 分钟
───────────────────────────────────────────────
总计                              约 4.5 小时
```

---

## Step 1：AiAuditService（核心）

### Step 1.1 — 创建审核 Prompt 模板

**目标**：定义 AI 审核的 system prompt 和 user prompt 模板

**输入**：无

**输出**：审核维度 + JSON 输出格式

**System Prompt**：
```
你是 OrangeJD 平台的 AI 内容审核员。审核用户提交的作品/需求。

审核规则（按优先级）：
1. 【红线-必拒】内容涉及血腥暴力、色情、政治敏感、违法犯罪、不良诱导 → 直接拒绝
2. 【基础质量】标题有意义且长度 ≥ 2 字；简介/描述 ≥ 10 字 → 否则拒绝
3. 【封面图】图片含违规内容 → 拒绝；图片无法判定 → 跳过图片维度
4. 【倾向通过】只要不触犯红线、描述基本完整 → 通过

输出严格 JSON（不要额外文字）：
{"action":"APPROVED","reason":"标题清晰，描述完整，内容合规"}
或
{"action":"REJECTED","reason":"标题过于简单（仅1字），缺乏项目描述"}
```

**User Prompt 模板**：
```
请审核以下内容：

标题：{title}
简介：{summary}
详情：{detail}
分类：{categoryName}
演示链接：{demoUrl}
提交者：{username}
```

---

### Step 1.2 — 创建 AiAuditService

**目标**：调 DeepSeek V4 Pro，返回审核决策

**新增文件**：`orange-server/src/main/java/com/orangejd/server/service/AiAuditService.java`

**核心方法**：

```java
@Service
public class AiAuditService {
    // 审核单条作品
    public AuditResult auditWork(Long workId);

    // 审核单条需求
    public AuditResult auditRequirement(Long requirementId);

    // 批量审核所有待审
    public int auditAllPending();
}
```

**AuditResult**：
```java
class AuditResult {
    String action;   // "APPROVED" / "REJECTED" / "SKIPPED"
    String reason;   // 审核理由
    boolean degraded; // 是否降级（图片失败后纯文本审）
}
```

**API 调用逻辑**：

```
1. 查作品详情（title/summary/detail/coverUrl/demoUrl/category/owner）
2. 截断 detail 到 3000 字符
3. coverUrl 转为完整公网 URL（http://47.116.8.45/uploads/xxx）
4. 组装多模态请求体（文本 + image_url）
5. POST {endpoint}/chat/completions（不设超时）
6. 如果响应含图片错误 → 去掉 image_url 重试 → 标记 degraded=true
7. API 完全失败 → 重试 1 次 → 仍失败 → 返回 SKIPPED
8. 解析 JSON → 正则兜底 → 返回 AuditResult
```

**JSON 解析兜底**：
```
优先：JSON.parse(response) → action, reason
兜底：正则匹配 /通过|APPROVED|批准/ → APPROVED
              /拒绝|REJECTED|不通过/ → REJECTED
              /reason|理由|原因[：:]\s*(.+)/ → reason
```

**并发控制**：
```
线程池：newFixedThreadPool(2)
提交任务 → 线程池满了 → 排队等待
```

**图片降级逻辑**：
```
POST（带图）→ 返回 error 含 "image" / "图片" / "invalid_image" → 
  飞书通知: "《xxx》图片审核失败，已降级为文本审核" →
  POST（不带图）→ 继续审核
```

**输出**：1 个 Service 类

**验证**：curl 手动调 auditWork(1) → 查 DB 审核记录

---

### Step 1.3 — 改造 AuditServiceImpl（支持 AI auditor）

**目标**：允许 AI 作为审核人（auditorId=0）

**输入**：`AuditServiceImpl.java`

**改动**：
- 去掉 `BaseContext.getCurrentId()` 的强制依赖
- 当 auditorId 为 null 或 0 时标记为 AI 审核
- auditorId=0 时跳过 "审核人不是管理员" 的检查

**输出**：修改 1 个方法

**验证**：AI 审核后 audit_record 中 auditor_id=0

---

## Step 2：自动触发机制

### Step 2.1 — 全局开关 Controller

**目标**：Redis 读写开关状态

**改动文件**：`AdminAuditController.java`

**新增端点**：

| 方法 | 路径 | 用途 |
|------|------|------|
| `GET` | `/admin/audit/ai-auto/status` | 返回 `{enabled: true/false}` |
| `PUT` | `/admin/audit/ai-auto/toggle` | 翻转开关 |
| `POST` | `/admin/audit/ai-auto/run` | 手动触发全量审核 |

**开关实现**：
```java
// 读
redisTemplate.opsForValue().get("system:ai-audit:enabled")
// 写
redisTemplate.opsForValue().set("system:ai-audit:enabled", "true")
```

**验证**：curl PUT → GET 确认状态变化

---

### Step 2.2 — 新提交自动触发

**目标**：用户提交作品/需求后，若开关开启则自动 AI 审核

**改动文件**：`WorkService.java`（create 方法）、`RequirementService.java`（create 方法）

**改动**：在 create() 末尾加：
```java
if (aiAuditEnabled()) {
    aiAuditExecutor.submit(() -> aiAuditService.auditWork(work.getId()));
}
```

**异步执行器**：
```java
// AiAuditService 中
private final ExecutorService executor = Executors.newFixedThreadPool(2);
```

**验证**：开开关 → POST 提交作品 → 等 5 秒 → 查 audit_status 已变化

---

## Step 3：飞书通知

### Step 3.1 — 创建 FeishuNotifyService

**目标**：审核完成后 POST 飞书 Webhook

**新增文件**：`orange-server/src/main/java/com/orangejd/server/service/FeishuNotifyService.java`

**消息格式**：

通过（绿色卡片）：
```json
{
  "msg_type": "interactive",
  "card": {
    "header": { "template": "green", "title": {"content": "✅ AI 审核通过"} },
    "elements": [{
      "tag": "markdown",
      "content": "**作品：**《xxx》\n**作者：**user123\n**审核人：**🤖 AI\n**理由：**标题清晰，描述完整"
    }]
  }
}
```

拒绝（红色卡片）：
```json
{
  "msg_type": "interactive",
  "card": {
    "header": { "template": "red", "title": {"content": "❌ AI 审核拒绝"} },
    "elements": [{
      "tag": "markdown",
      "content": "**作品：**《xxx》\n**作者：**user123\n**审核人：**🤖 AI\n**拒绝理由：**标题'123'无意义，内容过于简单"
    }]
  }
}
```

**调用方式**：
```java
// AiAuditService 审核完成后调用
feishuNotifyService.sendAuditResult(workTitle, authorName, action, reason);
```

**容错**：
- Webhook POST 失败 → log.warn → 不影响审核
- Webhook URL 为空 → 不发送

**验证**：AI 审核通过 + 拒绝 → 飞书群收到两条不同颜色卡片

---

## Step 4：前端控制栏

### Step 4.1 — 改造 Audit.vue

**目标**：审核列表顶部加 AI 控制栏

**输入**：`orange-admin/src/views/Audit.vue`

**新增 UI**：

```
┌──────────────────────────────────────────────────────────┐
│ 🤖 AI 托管  [el-switch]    [立即审核全部 (el-button)]     │
│ 状态：待审 5 条 | 审核中 2 条 | 已完成 3 条              │
└──────────────────────────────────────────────────────────┘
```

**表格新增列**：审核人列显示 `🤖 AI` 或 管理员用户名

**交互**：
- 开关切换 → 调 `PUT /admin/audit/ai-auto/toggle`
- 点「立即审核全部」→ 调 `POST /admin/audit/ai-auto/run` → 轮询进度
- 审核完成后列表自动刷新

**输出**：修改 1 个 Vue 组件

**验证**：浏览器操作 → 开关切换 → 表格刷新 → AI 审核记录显示 🤖 AI

---

## Step 5：配置 + 测试

### Step 5.1 — 配置文件

**目标**：AI 审核和飞书参数可配置

**application-dev.yml**：
```yaml
orange:
  ai-audit:
    endpoint: ${AI_AUDIT_ENDPOINT:https://api.deepseek.com/v1}
    api-key: ${AI_AUDIT_KEY:}
    model: ${AI_AUDIT_MODEL:deepseek-v4-pro}
  feishu:
    webhook-url: ${FEISHU_WEBHOOK_URL:}
```

**application-prod.yml**：同上，无默认值

**start.sh 追加**：
```bash
export AI_AUDIT_ENDPOINT=https://api.deepseek.com/v1
export AI_AUDIT_KEY=sk-xxx
export AI_AUDIT_MODEL=deepseek-v4-pro
export FEISHU_WEBHOOK_URL=https://open.feishu.cn/open-apis/bot/v2/hook/xxx
```

---

### Step 5.2 — 本地全链路验证

| # | 验证项 | 方式 |
|---|--------|------|
| 1 | 开关持久化 | curl PUT toggle → 重启 → GET status 确认 |
| 2 | 正常作品 AI 通过 | 开开关 → 提交合规作品 → 等 5s → audit_status=APPROVED |
| 3 | 违规作品 AI 拒绝 | 提交标题"1"的作品 → AI 拒绝 + reason 含理由 |
| 4 | 飞书通过卡片 | 飞书群收到绿色卡片 |
| 5 | 飞书拒绝卡片 | 飞书群收到红色卡片 |
| 6 | 图片降级 | 模拟图片不可用 → 飞书收到降级通知 |
| 7 | API 失败 | 用无效 endpoint → audit_status 保持 PENDING |
| 8 | 不阻塞提交 | POST 作品 → 响应 < 1s |
| 9 | 手动审核仍可用 | 关开关 → 管理员手动通过/拒绝 |
| 10 | AI 记录显示 🤖 AI | 管理端列表验证 |

---

## 2. 回滚策略

| 操作 | 回滚 |
|------|------|
| 关闭 AI 托管 | `PUT /admin/audit/ai-auto/toggle` |
| AI 误审 | 管理员手动改为通过/拒绝（覆盖 AI 结果） |
| 代码回滚 | `git revert` + 重新部署 |

---

## 3. 文件清单

| 层 | 新增 | 修改 |
|------|------|------|
| orange-server/service | `AiAuditService.java`、`FeishuNotifyService.java` | `AuditServiceImpl.java`、`WorkService.java`、`RequirementService.java` |
| orange-server/controller | — | `AdminAuditController.java` |
| orange-server/config | — | `application-dev.yml`、`application-prod.yml` |
| orange-admin | — | `Audit.vue` |
| 服务器配置 | — | `start.sh` |

**总计：新增 2 文件，修改 7 文件。**
