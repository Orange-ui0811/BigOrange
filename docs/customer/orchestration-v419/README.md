# 客服编排改进 v4.19

基线本地提交：`67381b6`。当前分支：`codex/customer-flow-improvements`。本轮保留 Java 在线服务、B 版 bge-m3/结构分块/混合检索以及 A 旧入口。

## 已实现

| 环节 | 变化 | 边界 |
|---|---|---|
| 意图与动作 | 一段带原话依据的 STATE/EXPLAIN 前置说明＋一次业务动作；混合请求不再被只读快捷分支吞掉 | 不是任意 Agent 循环，仍由 Java 调度 |
| 条件修复 | 单个 topic 格式错误进入字段校验，保留其他有效条件；前半句问号不把后半句明确取消变成重复确认 | 未理解的硬条件仍需澄清 |
| 指代 | 保留推荐列表顺序，单独记录 focusedProject；“第二个／它”进入限定项目详情检索 | 不能确定对象时询问，不猜项目 ID |
| 反馈记忆 | 用户明确不要的项目及原话保存在当前会话，按仓库去重排除，可明确恢复 | 最多 24 项；不写长期画像 |
| 历史 | 按完整运行裁剪，结构化回答先缩减字段再序列化；有消息 ID 的提取式摘要按预算产生 | 最近 40 条消息内的有界摘录，不是完整长期语义记忆 |
| 输入预算 | 理解提示词压缩重复指令，保留协议；增加长消息回归 | 6000 仍是保守字符估算，不是精确 tokenizer |
| 用途证据 | V43 事实表及审核事件表，原 5 条已核对证据幂等导入；草稿、审核、撤销、过期分离 | 原文出现某词不能自动批准相关性 |
| 离线处理 | 复用 SystemTaskService，每 5 分钟幂等创建来源扫描任务；提取原句成为草稿，资料失效标 STALE | 无模型调用，无自动审核；未覆盖来源仍走原在线核对路径 |
| 项目召回 | 显式 projectDiversity 参数，改写后的查询无需含“推荐”也会先选不同项目的片段 | 不增加调用，不修改查询文本；仍受当前召回窗口限制 |
| 运行治理 | 默认 2 个执行＋2 个等待，每账号仍一个；排队计入持久化的 90 秒总期限；取消排队不会执行 | 仍限单实例；重启中断未完成任务，不自动重放 |
| 阶段预算 | ACTION 最多 45 秒，QUERY_PLAN 12 秒，PURPOSE_REVIEW/EVIDENCE 30 秒，VERIFY/ADVICE 20 秒；重试共享阶段期限 | 同时受全轮剩余期限限制；数值仍需持续容量标定 |
| 评测与诊断 | 业务结果独立于运行 SUCCEEDED；分阶段耗时、失败尝试和用量；无明确评测要求标 UNASSESSED | Token 未上报、价格未知不能算成免费；不在用户气泡恢复 Token 展示 |

## 管理接口

沿用现有 ADMIN 鉴权，未新增公开写接口：

- `GET /admin/customer/source-facts`：来源事实和审核状态。
- `POST /admin/customer/source-facts`：提交原文事实草稿，包含 id、repository、field、sourceHash、quote、relations，可选 reviewedSource.versionId。
- `POST /admin/customer/source-facts/refresh`：提交持久化扫描任务。
- `POST /admin/customer/source-facts/{id}/review`：`revision`、`status`（REVIEWED/REVOKED）、`reason`；审核人从鉴权注入。审批前核对当前业务池的来源版本、哈希、原话。
- `GET /admin/customer/source-facts/{id}/events`：审核事件。
- `GET /admin/customer/runs/{id}/diagnostics`：运行状态、业务结果、版本、阶段记录及用量；不返回画像、聊天原文和原始证据。

relations 内置 ai-application/agent/rag，其他经人工核实的单主题可以用 `topic:规范化主题`；关系值 DIRECT/SUBTOPIC。宽泛领域有特定场景或否定限定时仍需要语义核对，不能借已有标签绕过要求。

数据库只增加 V43 两张表。文件种子使用 INSERT IGNORE，不覆盖已撤销记录。草稿变化不刷新已批准证据版本；审核、撤销影响后续缓存；来源正文/版本变化在使用时立即拒绝旧证明，后台状态标记允许随后完成。当前运行提交前也检查证据目录版本。

## 参考机制

- DeepSeek Harness：完整轮上下文边界、独立焦点记忆、全部调用尝试的用量汇总。没有引入 Node 运行时或自动 LLM 长期摘要。
- QueryMind：有界动作计划、恢复状态、明确业务验收要求。没有复制 SQL 工具或无限循环，没有把“未定义预期”算通过。
- Spring AI Alibaba Examples：结构化动作协议、上下文参与检索、显式检索策略。复用现有 B 检索，不迁入整个示例工程。
- 平台自身任务中心：来源事实扫描、重试与幂等，避免另建一套 Python 在线服务。

## 验收

实际结果见 [verification.json](verification.json)、[semantic-probe.json](semantic-probe.json)、[persistence/persistence.json](persistence/persistence.json)。

真实 Flash 测试仅发送 8 条合成需求及合成会话状态，不包含真实账号画像、会话历史、项目证据。8/8 通过、8 次调用无重试、合计 44,666 个供应商报告 Token，单条理解阶段 1.55–4.73 秒。金额未知。这些结果只能说明此次动作与条件协议通过，不能作为完整推荐准确率、引用准确率或全链路延迟。

仍需独立验证：全量来源人工审核覆盖、真实推荐与引用质量、持续容量、服务器发布与观察期。多查询独立召回、模型 Rerank、生成式长期摘要均需对照实验后决定；本轮没有为了功能数量默认增加这些调用。

## 本地运行与回退

后端启动由 Flyway 应用 V43；执行器恢复中断运行；首次启动导入已有审核种子。管理员可撤销单条来源证据。`orange.customer.max-queued=0` 关闭等待队列，`orange.customer.enabled=false` 关闭客服。

回退代码时保留 V43 表和会话数据；旧 A 索引与入口仍可用。不要清空向量库、删除会话或直接覆盖整个工作区。`67381b6` 是本轮前的恢复检查点。当前目录还有其他模块的独立修改，本轮改进提交只包含客服、关联检索与验收文件。
