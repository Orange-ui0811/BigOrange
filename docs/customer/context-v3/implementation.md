# 第三阶段：上下文工程与有限 Spring AI 接入

日期：2026-10-29。用户确认：1A 上下文工程；2A 结构化状态＋原话＋按需摘要；3A 同会话按任务隔离；4A 自动回查后仍不明确则追问；5A 有限接入 Spring AI。

## 实施范围

现有 LangGraph4j 业务图继续组织执行，理解和校验阶段接入上下文服务。没有新增 Python 服务。B 版知识库、需求来源校验、项目证据与导入资格判断继续使用原逻辑。只修改本地项目。

| 任务 | 实际实现 | 验证入口 |
| --- | --- | --- |
| P3-S0 | `ExternalAiClient → Spring AI ChatClient → CustomerSpringAiChatModel` | CustomerSpringAiChatModelTest、CustomerThinkingProtocolTest |
| P3-S1/S2 | CustomerContextPacket、CustomerContextBudget、CustomerContextAssembler；CustomerDialogue 兼容接口 | CustomerContextAssemblerTest、CustomerDialogueTest |
| P3-S3 | CustomerHistoryRecovery 的受控 ToolCallback，CustomerContextSupport 接入理解前回查 | CustomerHistoryRecoveryTest、CustomerContextSupportTest、CustomerToolsTest |
| P3-S4 | CustomerSummaryService、CustomerContextRepository/Mapper、V46 数据迁移 | CustomerSummaryServiceTest、CustomerContextMysqlIntegrationTest |
| P3-S5 | 每次模型输入预算 manifest；理解上下文来源 manifest；回查/摘要维护记录 | CustomerUsageLedger、customer_step、customer_raw_payload |
| P3-S6 | 断网模拟、协议回环、独立临时 MySQL；真实模型验收另列 | verification.json |

Spring AI 使用当前 1.0.0 API。实现的是自定义 ChatModel 驱动：动态配置、DeepSeek thinking、native customerAction、有界 HTTP 和提供方响应解析仍由驱动管理，未迁移到官方 OpenAI starter。ChatClient 使用 NoOpTemplateRenderer 原样传递 JSON；不自动注入窗口记忆，不启用内部工具循环或框架重试。框架提供统一消息和响应接口，不替代业务规则。

## 请求流程

1. 固定会话、任务/需求版本和当前消息 ID；未获得稳定消息 ID 的历史不声称可以原文回查。
2. 最近窗口最多读取 80 条同任务或任务归属未知的消息；其他任务的普通历史不自动混入。
3. 确定性指代解析后，出现历史指代或歧义时回查原文。模型也可返回 `needsHistory/historyQuery`，在没有查过且时间足够时补查并重新理解一次。
4. 自动回查经过 Spring AI ToolCallback 和现有 CustomerTools 执行器：身份在服务端闭包中，单次 3 秒超时，共享整轮取消和时限。普通业务工具最多 5 次，历史读取另限 2 次，总计最多 7 次；知识搜索原有上限不变。
5. 只有用户明确恢复旧任务的措辞才允许查询同会话其他任务来源；模型查询文字不能扩大范围。其他任务/未归属旧消息标记为 historicalOnly。
6. 生成不可变 ContextPacket：包含节点、作用域、来源引用、摘要引用、预算、裁剪原因。普通 manifest 不记录原文；模型请求原文仍在现有受控 raw payload 体系中。
7. 未核实历史不能改变当前需求，所有提议继续验证当前消息原话。回查仍不足且模型声明需要更多历史时，返回澄清而非执行动作。
8. 需求核对等其他模型阶段继续使用各自专用输入，并由统一 ContextBudget/最终驱动检查完整请求。
9. reconcile 后绑定本次 run 与有效 task/requirementVersion。结果校验末尾可尝试分段摘要；不修改回答、任务或授权。

## 预算与摘要

- 逻辑输入按现有保守 code-point 方法计数。提供方实际 usage 才是成本依据，不把字符估算当作精确 token。
- 逻辑消息预算仍为 6000；最终 HTTP 请求另检查完整序列化 body，包括工具 Schema 和响应格式。`orange.customer.wire-input-code-points` 默认 20000，允许 512–24000；任一上限超过均在 HTTP/验收计数之前阻断。两种计数是独立估算，不把 Schema 成本隐去。
- 本地纯计算样本：实际 ACTION 提示词＋小型任务输入逻辑 3371，普通 JSON 完整体 3694，native action 完整体 13939 code points。因此不将完整体上限错误复用为逻辑输入上限；Schema 保留原规则，没有为缩小请求削弱校验。
- 当前消息、当前有效需求与必要来源不静默截断；必要信息放不下明确阻断，保留业务状态。
- 完整近期交互窗口最多约 1400 估算字符，摘要不超过节点预算的四分之一；工具调用与结果保持配对。
- 分段摘要只取当前任务已成功完成的原始历史，保留最近 3 个完整轮次。较早可归档内容达到 2800 字符才考虑生成。
- 助手消息原本含整份 result JSON；摘要输入投影为完整 answer/status/pendingQuestion 和项目身份，剔除重复 state/audit。回答的否定与限定不截断，持久化来源哈希仍对应完整原始消息，回查也能读完整原文。触发阈值按投影后的有效内容计算。
- SUMMARY 单独限输入 min(4000,全局)、输出 1000、阶段 8 秒、thinking=false、一次尝试。没有足够剩余时间或验收额度时跳过。
- 摘要只从完整原文轮次生成，不反复摘要旧摘要。单个过大轮次放不下则跳过，原文保留。
- 来源 ID、哈希、任务身份、活动运行、租约及重叠范围均校验后写入。哈希只证明来源一致，不能证明模型摘要语义正确。
- 当前实现是在有效回答提交前的末尾做有限维护，可能增加最多一个摘要调用的延迟；没有后台摘要调度器。取消/整轮超时仍由现有执行器决定能否提交。

## 存储与兼容

V46 添加 customer_context_run、customer_context_summary、customer_context_summary_lease。原始消息保留在 customer_message，不复制整份聊天归档，不重写旧 state_json。旧 summary_text 不再在读取历史时被覆盖，也不被当作已确认来源载入新上下文。

没有回填猜测旧消息的任务归属：旧记录为 UNASSIGNED_LEGACY，只作历史线索；当前有效任务需求和原话来源继续保留。源数据与需求的现有保留策略未改变。新摘要以任务隔离、带来源的独立记录保存。

## 验收和边界

验收覆盖 P3-AC01 短对话回归、AC02 长历史保留、AC03 更正、AC04 任务隔离、AC05 歧义追问、AC06 预算/摘要失败、AC07 来源归属与迟到写入、AC08 可追溯诊断、AC09 框架协议与执行保护。

固定样本包含 60 轮历史及硬要求/更正，但这不等于 60 轮真实模型验收。MySQL 集成使用独立空数据库，执行后删除该临时库，不操作业务表数据。协议测试只访问回环 HTTP，无真实提供方请求。

最终验证：完整相关回归 507 项，484 通过、23 条件跳过、0 失败/错误；其中 4 项 MySQL 用例随后在独立临时库全部通过。这 4 项属于完整回归的跳过项，不额外算作新用例；合并证据为 488 个不同用例已执行通过、19 个仍条件跳过。见 offline-regression.json、mysql-integration.json。

真实 HTTP 冒烟使用原验收合成账号与新会话 573，两个确定性 STATE 请求都得到 CONFIRMED_CONDITIONS；写入 2 条任务绑定、2 条上下文 manifest，第二轮执行 1 次受控历史搜索。没有模型调用，不代表真实模型理解、摘要或推荐语义通过。见 http-smoke.json。后端 18080 健康检查 db/redis/rag_vector_store 均为 UP；执行器仍为 LANGGRAPH4J，原 50/50 守卫保持启用，账本哈希与实施前一致。

验证期间修正了旧测试仍要求无版本覆盖 summary_text 的断言、历史归属返回值检查、原生 Schema 的独立完整体预算及临时 MySQL 夹具的重复 run/role。失败过程日志保留于 `_staging/context-p3-regression.log`、`context-p3-regression-final.log`；最终通过日志为 `context-p3-regression-verified.log`、`context-p3-mysql.log`。

历史检索当前是有限的词面匹配，最多查询 20 条匹配与 8 条较新的可能更正；每轮最多 2 次读取，片段可能不完整。系统因此不能承诺找到全部历史或理解全部指代；必要时追问。当前任务状态仍是有效要求的依据。

完整执行中断恢复、实时 GitHub 搜索、跨会话个人记忆和大规模推荐质量评估不在本阶段。后续已在独立授权40次额度下执行真实验收38次：上下文固定场景通过，发现并修复任务控制问题，另有两项推荐质量问题未解决；见 [真实验收复盘](live-acceptance-review.md)。实际 token 节省仍缺少公平对照，未承诺端到端性能。原账本保持50/50不变，新账本独立保留38/40。

## 回退与复盘

真实验收发现的两项推荐质量问题已补充代码修正、目录探针及旧结果导入保护；完整说明见 [推荐适配修正](recommendation-fit-correction.md)。代码/离线检查通过，真实推荐复测待完成；历史失败结论保留，发布验收仍未通过。

本轮前源码快照：`_staging/context-p3-20261029-143422`。逐文件比较恢复本轮改动，保留其他未提交工作；不要执行 git reset --hard。V46 为增量表，源码回退时可保留表，不清除用户会话或原始消息。

建议阅读顺序：CustomerContextSupport → CustomerContextAssembler → CustomerHistoryRecovery → CustomerSummaryService → CustomerContextRepository；再读 CustomerSpringAiChatModel 与相应测试。测试统计、运行健康和账本哈希见 verification.json。
