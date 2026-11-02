# 客服需求与任务状态契约

版本：workflow-v2-design-1。日期：2026-10-29。拟实现契约，不是当前数据库结构。产品决定见 [流程规范](01-routing-spec.md)。

## 状态分层与身份

| 层 | 标识 | 权威内容 |
| --- | --- | --- |
| 会话 | sessionId、ownerId、sessionRevision | 原始消息、活动任务引用、历史任务 |
| 推荐任务 | taskId、requirementVersion | 当前目标、需求集合、问题记录、候选列表、阶段 |
| 单轮运行 | runId、requestId、messageId、inputHash | 本轮执行状态、超时、工具回执、用量 |
| 候选列表 | listId、taskId、requirementVersion | 当时展示的顺序与项目身份，不随新列表重排 |
| 问题 | questionId、taskId、createdRevision | 关联要求、来源、答案和生命周期 |

requestId 幂等与 sessionRevision 并发校验是不同职责。任务失败和请求失败不同；推荐结果可用与 HTTP 成功不同。

## 本轮理解结果

建议字段：rawMessage、resolvedQuery、intent、intentSubtype、taskRef、references、actions、requirementProposals、ambiguities、beforeTask。

- rawMessage 不改写；resolvedQuery 仅用于理解与检索。
- references 每项记录 kind（PROJECT/QUESTION/TASK/REQUIREMENT）、服务端标识、listId/position（如适用）、sourceMessageIds、resolution（RESOLVED/AMBIGUOUS/UNRESOLVED）、method（UI/POSITION/FOCUS/SEMANTIC）。
- 语义模型只能提议标识和变更，后端验证归属、来源与当前有效性。
- intent 的合法集合与路由表见 01；actions 可以有 ANSWER_QUESTION、UPDATE_REQUIREMENT、DISMISS_PROJECT、RESTORE_PROJECT、PAUSE_TASK、RESUME_TASK、SWITCH_TASK、SELECT_PROJECT、CANCEL_TASK、RECORD_ONLY、SKIP_QUESTION。
- 一轮可同时产生多个需求变更，但只推进一个任务。纯解释无需求变更；“解释并修改”的 beforeTask 读取变更前快照。
- QUESTION 的答案可以是对开放问题的引用，原话不一定重述字段名称；需要 questionId 与问题语义共同支持。

## 需求条目

每条需求字段：

| 字段 | 语义 |
| --- | --- |
| id | 稳定身份，编辑时不创建无关联的新条目 |
| taskId、revision | 所属任务和条目版本 |
| kind | GOAL、FEATURE、TECHNOLOGY、RESOURCE、TIME、EXPERIENCE、PREFERENCE 或开放类型 |
| text、normalizedValue | 原始含义与可执行值；单位、范围、否定必须保留 |
| strength | HARD 或 SOFT；用户基础默认不转成语言硬限制 |
| assertion | EXPLICIT、INFERRED；推测不参与硬过滤 |
| status | ACTIVE、PENDING、REMOVED |
| source | messageId、quote、来源类型；引用历史沿用时另记本轮授权原话 |
| verification | STRUCTURED、SEMANTIC、UNSUPPORTED；无法核实的要求仍保留 |
| supersedesRevision | 修改关联到旧版本，旧版留作解释依据 |

新增、替换、取消、硬软调整分别产生事件。取消用 REMOVED；不要用“值为空”同时表示未提供与已取消。

原话完整表达多个要求时全部提取；重复同一含义不新增条目。用户明确改口优先于旧版；两项同时有效且冲突时保留事实并澄清，不擅自删一项。关键条件变更使 requirementVersion 增长；仅闲聊、查看、重复提交不增长。

不能把“我会 Java”变成“只推荐 Java”；不能把“支持扫码吗”变成“必须支持扫码”；不能把网页或仓库中的指令变成用户需求。

## 推荐任务与转换

taskId、goal、status、requirementVersion、requirements、openQuestionId、currentListId、focusedProject、excludedProjects、selectedProject、lastOutcome、pendingOperation 是任务的核心字段。

| 当前状态/事件 | 后续状态与动作 |
| --- | --- |
| 新目标 | DISCOVERING |
| DISCOVERING / EVALUATING 中存在需用户决定的关键问题 | WAITING_USER；发布一条 OPEN 问题 |
| WAITING_USER 获得足够答案 | READY_TO_SEARCH；旧问题 ANSWERED |
| 答案改变问题但仍不充分 | 旧问题 ANSWERED/SUPERSEDED；发布不同问题，仍 WAITING_USER |
| 可搜索但用户要求仅记录 | 保留 DISCOVERING 或 READY_TO_SEARCH，不执行搜索 |
| 开始核对候选 | EVALUATING |
| 展示候选 | PRESENTED；不是 COMPLETED |
| 选定唯一项目或明确结束 | COMPLETED；选定不等于导入成功 |
| 明确暂停/切换 | PAUSED；解除旧活动指针；切换时创建新任务 |
| 明确取消 | CANCELLED；取消开放问题和未执行操作 |
| 恢复暂停任务 | 验证需求、问题、候选时效后进入 DISCOVERING/READY_TO_SEARCH/PRESENTED |
| 修改已完成任务的目标 | 显式重开或新任务并记录来源；不可悄悄修改历史完成快照 |

运行失败保持需求不变，写 lastOutcome=SERVICE_UNAVAILABLE/INTERRUPTED 等；若已保存中间阶段，下一轮核实旧 run 已终止，清除“仍在执行”的显示，进入可重新评估状态。不能单靠 status=EVALUATING 判断后台正在工作。

## 问题生命周期与关联

questionId、taskId、kind（REFERENCE/REQUIREMENT/TRADEOFF）、text、options、allowFreeText、relatedRequirementIds、createdRequirementVersion、validityFingerprint、sourceMessageId、status、answerMessageId、askedReason 为建议字段。

- 同一活动任务最多一条 OPEN 主要问题；替换时先将原问题 SUPERSEDED。
- ANSWERED、SKIPPED、SUPERSEDED、CANCELLED 为问题终态；不能复用旧 questionId 发布新问题。
- “不知道/跳过”写 SKIPPED，不填入猜测值；是否继续取决于问题是否阻塞关键动作。
- 插问不改变 OPEN；真正回答问题并含额外条件时，同时处理答案及额外条件。
- 版本不同不必一律拒绝。先检查相关需求、候选和问题是否仍适用；不影响的问题可接受答案并记录重验证。受影响的问题重新澄清。
- 未携带 questionId 的文字回复，通过唯一 OPEN 问题和语义关联；存在多个可能对象时不得硬套。

## 候选与匹配

projectKey 使用服务端确认的项目身份，外部项目用规范化仓库身份并关联资料版本。listId 固定当时顺序；点击位置必须与该列表一起解析。

每项 requirementMatches 记录 requirementId、requirementRevision、verdict、evidenceRefs、checkedAt、sourceVersion、reason、adaptationNote。

verdict：SATISFIED / PARTIAL / CONFLICT / UNKNOWN。PARTIAL 对硬要求不能等价于通过；已知不满足的必要部分应标 CONFLICT。adaptationNote 不能把“可改造”升级为 SATISFIED。

- EXPLORATORY：排除已知硬冲突；硬要求 UNKNOWN 可进入待考察列表并显式标注。
- STRICT：所有有效、明确硬要求均有满足依据；UNKNOWN/PARTIAL 不进入合格推荐。
- 用户尚未说清的硬要求与项目资料未知不同，前者走澄清；用户坚持先看时仅给未完成筛选的探索结果，不声称整体匹配。
- 需求改变后旧匹配结果标过期；未改变的原始事实可复用，须按新需求重新形成匹配结论。
- 任务缺候选不代表世界上不存在项目；覆盖与失败状态见 03。

## 示例

```json
{
  "schemaVersion": 2,
  "sessionRevision": 8,
  "activeTaskId": "T-SHOP",
  "task": {
    "id": "T-SHOP",
    "status": "WAITING_USER",
    "requirementVersion": 2,
    "requirements": [{
      "id": "R-OFFLINE", "revision": 1, "kind": "RESOURCE",
      "text": "日常使用不能联网", "normalizedValue": {"runtimeNetwork": false},
      "strength": "HARD", "assertion": "EXPLICIT", "status": "ACTIVE",
      "source": {"messageId": "M-12", "quote": "日常使用不能联网", "type": "MESSAGE"},
      "verification": "SEMANTIC"
    }],
    "openQuestionId": "Q-DEPLOY",
    "currentListId": null
  },
  "question": {
    "id": "Q-DEPLOY", "taskId": "T-SHOP", "kind": "TRADEOFF",
    "text": "你希望直接部署，还是接受在现有代码上改造？",
    "options": ["直接部署", "接受改造"], "allowFreeText": true,
    "relatedRequirementIds": [], "createdRequirementVersion": 2,
    "validityFingerprint": "use-mode-unset", "status": "OPEN"
  }
}
```

此例只展示一个任务片段，不代表完整请求结构；测试用例使用简化投影，规则见 conversation-cases.json 的 projectionContract。

## 并发、保存与兼容

- 服务端检查会话/任务/列表/问题归属；归属不通过不得读取或变更其他用户状态。
- 相同 requestId 且相同 inputHash 返回原结果；相同 requestId 不同内容返回冲突，不重用旧回复。
- 提交状态时对 sessionRevision 做并发校验，旧运行不能覆盖新状态；状态与回复按现有事务边界提交。
- 状态采用 versioned 新空间，例如 state_json.workflowV2；保留旧数据。旧字段通过确定性映射提取，保留来源，未知不补造。
- 历史 lastQuestion 只有文字/runId，不能伪造为新协议下可执行的操作确认；可作为理解线索，必要时重新澄清。
- 旧候选没有 listId 时仅可受控关联最新可见列表并记录 legacy 来源，歧义时重新选择。
- 一次运行绑定一种状态模式；不能让 v1/v2 同时写入同一活动任务。未来回退先暂停新模式，不把 v2 新字段冒充旧协议。
- schemaVersion=2 是目标契约版本，不代表数据库已经升级。
