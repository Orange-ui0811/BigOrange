# 客服流程设计与后续实现映射

日期：2026-10-29。这里描述下一阶段的代码工作，不表示这些改造已经执行。

> 第二阶段已开始实施，实际文件、验证结果与未完成项以 [第二阶段实施记录](05-stage2-implementation.md) 为准。下文保留第一阶段设计基线；其中灰度设想已被用户确认的“直接替换现有入口”取代，旧会话改为先确认需求，不自动沿用旧候选和执行进度。

## 本阶段任务映射

| 任务 | 已交付设计 | 检查方式 | 验收 |
| --- | --- | --- | --- |
| P1-S1 路由与指代 | 01-routing-spec.md | 八类意图、六条路线、入口阻断与旧意图映射 | AC-01、AC-05、AC-08 |
| P1-S2 状态契约 | 02-state-contract.md | 身份、来源、版本、问题生命周期及转换 | AC-02、AC-03、AC-06 |
| P1-S3 追问策略 | 03-clarification-policy.md | 用户歧义与资料缺口分开，候选差异驱动，停止与预算 | AC-03、AC-04、AC-05 |
| P1-S4 多轮场景 | conversation-cases.json | 两条连续主线和独立边界场景，逐轮预期状态 | AC-01 至 AC-06、AC-08 |
| P1-S5 实现与验收映射 | 本文件、acceptance.md | 代码证据、依赖、兼容和未实现边界 | AC-07 及所有标准 |

AC-08 是用户指出指代前置后增加的标准，原 AC 编号保留。执行依赖 P1-S1 → P1-S2 → P1-S3 → P1-S4 → P1-S5。

## 现有代码与参考证据

以下 OrangeJD 路径均相对仓库根目录，文件存在已于本阶段核对；代码存在不等于测试运行通过。

| 现有位置 | 当前职责 | 后续改造 |
| --- | --- | --- |
| orange-server/src/main/java/com/orangejd/server/controller/app/AppCustomerController.java | 会话与运行、事件、取消和导入入口 | 保留鉴权，增加可选 task/question/list 引用及旧客户端适配 |
| orange-server/src/main/java/com/orangejd/server/customer/CustomerRuns.java | 去重后的执行、超时、取消、引擎选择 | 保留；绑定一次运行的契约版本，避免两种状态模式同时写 |
| orange-server/src/main/java/com/orangejd/server/customer/CustomerStore.java | 状态与消息持久化 | 设计 sessionRevision 并发更新与新状态空间；不直接覆盖旧状态 |
| orange-server/src/main/java/com/orangejd/server/customer/CustomerState.java | 条件、意图、原话校验和固定追问 | 拆分开放需求与路由规则；保留原话校验 |
| orange-server/src/main/java/com/orangejd/server/customer/CustomerTurnPlan.java | 混合说明与位置/关注项目解析 | 前置指代关联；保留确定性身份解析，增加 listId 和问题版本 |
| orange-server/src/main/java/com/orangejd/server/customer/CustomerConversation.java | 集中业务分支与执行 | 渐进提取六条路线，保留最终结果校验 |
| orange-server/src/main/java/com/orangejd/server/customer/CustomerTaskProgress.java | 单 run 的推荐阶段 | 与新的多轮任务生命周期分开，旧失败记录保留 |
| orange-server/src/main/java/com/orangejd/server/customer/CustomerProjectFeedback.java | 项目排除与恢复 | 按 taskId 隔离，反馈不能改变无关需求 |
| orange-server/src/main/java/com/orangejd/server/customer/CustomerTools.java | 工具注册和执行限制 | 增加路线白名单；本阶段不新增 GitHub 工具 |
| orange-server/src/main/java/com/orangejd/server/customer/CustomerProjects.java | 候选、筛选与排序 | 通过适配器提供候选及结构化事实；后续需求匹配器消费 |
| orange-server/src/main/java/com/orangejd/server/customer/CustomerEvidenceSteps.java | 证据与项目卡片 | 复用引用及未知降级，不把待考察资格等同导入资格 |
| orange-server/src/main/java/com/orangejd/server/customer/LangGraphCustomerEngine.java | 图节点、条件边、检查点 | 组织入口与六条工作流；不靠框架自动决定业务含义 |
| orange-server/src/main/java/com/orangejd/server/customer/CustomerGraphState.java | 诊断摘要状态 | 不宣称现有 checkpoint 可完整恢复；完整执行恢复另设工作项 |
| orange-web/src/components/CustomerChat.vue | 客服消息、条件和卡片 | 自然语言主要问题、可选选项、跳过、先看、未知项和任务显示 |

现有测试阅读入口：

- orange-server/src/test/java/com/orangejd/server/customer/CustomerStateTest.java
- orange-server/src/test/java/com/orangejd/server/customer/CustomerTurnPlanTest.java
- orange-server/src/test/java/com/orangejd/server/customer/CustomerDialogueTest.java
- orange-server/src/test/java/com/orangejd/server/customer/CustomerGraphTest.java
- orange-server/src/test/java/com/orangejd/server/customer/CustomerGraphPersistenceTest.java
- orange-server/src/test/java/com/orangejd/server/customer/CustomerGraphEvidenceRoutingTest.java
- orange-server/src/test/java/com/orangejd/server/customer/CustomerGraphConversationRegressionTest.java

参考 Mewhelp 的实际位置：

- E:/Mewhelp/app/graph/build.py：公共入口、条件边和必经步骤。
- E:/Mewhelp/app/graph/routing.py：唯一意图映射、结束条件。
- E:/Mewhelp/app/graph/runtime.py：待办恢复与消息水位校验。
- E:/Mewhelp/app/core/intent.py：结构化输出；自报 confidence 不等于分类正确率。

本阶段主要重新核对路由代码；摘要和历史回查的完整移植属于下一阶段上下文工程，不纳入本次设计验收。

## 拟新增模块边界

以下名称与路径是目标，不是当前已实现类；均拟放在 orange-server/src/main/java/com/orangejd/server/customer/。

| 模块 | 输入 | 输出/职责 |
| --- | --- | --- |
| CustomerReferenceResolver | 原话、问题、列表、关注对象、必要历史 | 对象引用、来源和歧义；不得新造项目身份 |
| CustomerTurnInterpreter | 原话和消解结果 | 意图、动作、需求提议 |
| CustomerRequirementService | 提议、现有需求、原话与版本 | 校验后的需求事件与新版本 |
| CustomerRoutePolicy | 意图、动作、对象有效性、任务状态 | 工作流与前置阻断结果 |
| CustomerClarificationPolicy | 需求、问题记录、候选差异 | 问、查、展示、停止及原因 |
| CustomerTaskService | taskId、问题回答、运行回执 | 多轮任务与问题生命周期 |
| CustomerRequirementMatcher | 候选事实和需求版本 | 每项满足/冲突/未知；下一阶段最小闭环实现 |

逻辑节点不意味着独立服务或独立模型调用。模型消解和意图提取可合并请求，程序的关联校验仍必须先于有副作用的动作。

## 后续实施依赖

1. 实现版本化状态对象和确定性变更，采用合成理解结果测试需求保存、取消与幂等。
2. 接入指代、路由和追问决策，使用本阶段候选样例回放；此时只证明程序状态转换，不证明模型语义能力。
3. 复用当前 B 版检索和候选服务，接入真实模型形成两条最小推荐闭环；分别评估理解错误与检索错误。
4. 再完善节点级上下文、检索优化和 GitHub 扩展。不能让前面阶段依赖尚未存在的 GitHub 搜索工具。

本阶段的 GitHub 用例是接口契约验证。后续工具未就绪时真实流程应明确返回覆盖限制，不能报告已经搜索。

## 兼容与恢复方案

- 先建立新状态读取/写入适配层；保留原消息、旧状态和已保存结果。
- 运行绑定固定 schemaVersion；旧会话首次进入新模式时进行可追溯转换，不批量猜测旧用户要求。
- 灰度期间一个会话只用一个状态写入模式。切回旧模式时冻结新模式任务，不能无损恢复就解释限制，而不伪造字段。
- 原有旧意图和前端字段通过适配器兼容，固定问卷逻辑只在新路径被新策略替换。
- 检查点完整恢复不在本阶段。跨轮恢复首先依靠有效需求、问题和候选状态；服务重启后的新运行重新校验事实。

## 验证与风险

| 风险 | 触发条件 | 处理与后续证据 |
| --- | --- | --- |
| 自由需求能保存但不能执行 | 无字段或证据支持匹配 | 显式标 UNSUPPORTED/UNKNOWN；下一阶段匹配用例 |
| 模型关联错项目 | 多个列表、旧问题或含糊指代 | 关联门控与版本检查；AC-08 与 AC-06 |
| 状态写入竞争 | 取消、旧运行晚返回、重复提交 | 版本校验和幂等；后续数据库集成测试 |
| 旧会话来源不完整 | 旧 lastQuestion 无 ID | 仅当理解线索，不能升级操作授权 |
| 搜索承诺过度 | 只有零命中或服务故障 | 按 03 的 outcome 分类，展示范围而不承诺全网覆盖 |
| 设计用例冒充真实效果 | 只校验 JSON 或回放期望状态 | 所有材料保留 DESIGN 标识；真实评测另行记录 |

执行本阶段后可开始的第一个编码任务是“需求条目与任务状态的纯逻辑实现＋用例适配”，不是重写整个客服或换检索框架。
