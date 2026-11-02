# LangGraph4j 接入说明（2026-10-07）

当前交付状态：代码已接入，默认仍为 `LEGACY`；本地验收进行中，尚未满足全量发布条件。不要把管理页面可打开或单元测试通过理解为已上线。

## 1. 不变的边界

Java 21 / Spring Boot 内使用 `org.bsc.langgraph4j:langgraph4j-core:1.8.27`，未引入模型集成包。客服模型仍经 `CustomerModel → ExternalAiClient`；工具经 `CustomerTools`；B 库仍执行现有 bge-m3、结构分块、Redis 混合检索策略。Prompt 版本保持 `customer-v4.19-composite-turns`。本次没有迁移 A 库。

运行创建时把 `execution_engine` 固定到 `customer_run`。同 requestId 重放返回原运行，不重新选择引擎。`CustomerRuns` 选择 `LegacyCustomerEngine` 或 `LangGraphCustomerEngine`，拿到待提交结果后，再调用 `CustomerStore.complete` 原子保存运行结果、会话状态与助手消息。

`CustomerConversation.execute` 仅保留给既有测试兼容入口；生产执行器调用 `newTurn`，不调用这个会直接提交的兼容入口。旧执行器和新执行器共用业务步骤，因此共享逻辑回归需要回退整个部署版本，单改引擎配置不能修复。

## 2. 节点及路由

公共路径：`load_context → understand_turn → reconcile_conditions`。

| 意图 | 分支 |
|---|---|
| STATE / CHAT / EXPLAIN | direct_response |
| DIAGNOSE | diagnose |
| QUESTION | retrieve_knowledge → prepare_evidence → generate_claims → validate_citations → verify_claims → assemble_project_cards（问答形态） |
| DETAIL / COMPARE | load_project_details → 项目证据路径 |
| COLLECT / RECOMMEND | 按原有条件规则追问，或 plan_recommendation → recall_candidates → review_purpose → select_projects |

项目证据路径：`retrieve_project_evidence → prepare_evidence → generate_claims → validate_citations → verify_claims → assemble_project_cards → generate_advice`。空证据和可恢复的证据错误走既有降级出口；无候选和用途核对不可用保留各自业务结果。

所有正常业务结果经过 `finalize_response → validate_response → END`。失败、取消、超时不能越过最终提交保护。共 20 个具名节点、最多 32 次节点执行；没有并行图节点、自主 Agent 循环或图层模型重试。

关键实现位于 `orange-server/src/main/java/com/orangejd/server/customer/`：

- `CustomerConversation.Turn` 保存本轮业务操作和路由；`CustomerRecommendationSteps`、`CustomerEvidenceSteps` 承担共享步骤。
- `LangGraphCustomerEngine` 每轮创建图、节点闭包和独立 Saver；同步执行节点返回 completed future，不引入公共线程池。
- `LegacyCustomerEngine` 用顺序循环执行同一组业务步骤。
- `FrozenCustomerConversation` 是测试目录里的原实现冻结件，用于识别共享步骤提取是否造成退化，不能将新旧执行器都用共享逻辑后自比通过当作独立基线。

## 3. 三类状态

1. 正式会话 `customer_session`：跨轮条件、候选顺序和业务进度的权威来源。
2. 正式运行 `customer_run`：并发、截止时间、终态、结果与用量的权威来源。
3. 图快照：`customer_graph_execution / customer_graph_checkpoint`，只用于诊断。

每轮 `threadId=customer:{runId}`，`graphVersion=customer-graph-v1`，`stateSchemaVersion=1`。下一轮重新加载正式会话；不从上一轮检查点恢复。

`CustomerGraphState` 保存 JSON 摘要：执行位置、条件、意图、候选/证据身份与数量、索引代际等。完整模型输入、工具正文和业务对象保留在本轮 Turn / Context 中；因此这些快照**不能恢复完整运行**。不提供恢复、重放或编辑检查点接口。

## 4. 保存与故障

`V44__customer_graph_orchestration.sql` 是增量迁移。仅新增列和表，既有会话及消息不改写。

- 初始检查点在工具与模型调用前写入，失败即停止。
- 中间检查点失败停止后续节点，不自动改走旧引擎。
- 已确认条件通过现有业务 checkpoint 保存，回答失败不回滚条件。
- 图正常结束后由外层检查取消/截止时间/终态再提交。
- 业务提交后的诊断归档失败只告警，不改成功状态、不重复生成。
- 服务重启继续标记未完成运行 `INTERRUPTED / SERVER_RESTARTED`。

Saver 使用 Spring JdbcTemplate 短操作借还连接，不在数据库事务中等待模型。状态使用 JSON，UTF-8 上限 256 KiB；没有 Java 原生反序列化。

## 5. 治理与观测

运行仍遵守 2 个执行 + 2 个排队、每账号一个活动运行、从提交开始 90 秒、模型阶段有限尝试、每轮共用 5 次工具/2 次检索预算。单节点拆分不重置预算。

节点数据写入 `customer_node_execution`；实际模型请求/响应、工具调用和结果写入 `customer_raw_payload`；`customer_raw_access_audit` 在原文返回前事务写入。模型响应排除内部推理字段，认证头与密钥不进入采集协议。

工具线程显式接收当前运行/节点关联，并创建本线程检索计时范围。用量以不可变的完成尝试发布到线程安全账本；迟到用量仍可记账，迟到回答不能覆盖终态。费用没有可靠价格时继续显示 `UNKNOWN`，Embedding 用量未返回时明确标记 `NOT_REPORTED`。

当前普通诊断展示节点路径、条件快照、候选流失数量、证据身份、模型尝试和已采集的检索计时。它不是完整的事实正确性证明；不能据此宣称每条候选都已有逐项排除解释，或每次降级都已有独立图节点错误。

图创建/编译与检查点写入计时保存在每轮账本，随运行结果一次保存；没有为每个计时额外写一行数据库。管理端“编排开销”页展示这些分项；检查点页的条件变化只比较允许字段。旧记录未采集时明确显示为空。

## 6. 管理入口和权限

管理端菜单“客服运行”，地址 `/admin/#/customer-runs`。

- `GET /admin/customer/runs`：运行 ID、引擎、状态、业务结果、时间分页筛选。
- `GET /{id}/diagnostics`：节点与调用摘要，不自动加载模型原文。
- `GET /{id}/checkpoints`：检查点元信息与筛选后的摘要，不返回完整状态 JSON。
- `POST /{id}/raw-access`：提交 `payloadId + reason`，验证同一运行及有效期，审计成功才返回正文。

所有接口继续走现有有效 ADMIN 身份拦截。原文另需 `CUSTOMER_RAW_ACCESS_ENABLED=true`，默认 false。开启原文权限不等于允许用户端访问这些接口。原文/检查点 30 天，节点/执行摘要及访问审计 90 天，每日分批清理，活动运行不清理；会话删除立即清理新增原文和检查点，既有聊天历史规则不改变。

## 7. 测试入口

完整结果和限制见 [验收状态](ACCEPTANCE.md)。

```powershell
# 默认本地确定性回归，不进行外发
mvn -o -pl orange-server -am test '-Dtest=Customer*Test,ArchitectureGovernanceTest' '-Dsurefire.failIfNoSpecifiedTests=false'
# 新建专用数据库执行 MySQL、Flyway、连接释放和固定响应基准
python scripts/customer_graph_persistence.py
# 需要另有明确外发授权；不是默认回归的一部分
python scripts/customer_graph_provider_acceptance.py
```

不要输出容器完整环境变量、数据库密码或浏览器登录 Token。真实模型对照用合成账号标识和合成条件，替换画像读取，不用真实账号历史；HTTP 鉴权另行验收。
