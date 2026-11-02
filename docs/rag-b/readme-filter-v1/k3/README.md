# K3：模型辅助选择原文知识块

已实现 Java 模型辅助选择器、Spring AI 适配、输出校验、磁盘缓存、持久调用账本及失败回落。当前提供冻结样本的离线规划和有限真实验证入口；生产入库与活动索引接入属于 K4。

最新进展见 [V1.6/V5 真实试跑](quality-v2/v5-pilot-v3/findings.md)：新授权 18/18 次已执行，5 仓库／101 条标签的诊断精确率 97.92%、范围内一致率 80.33%、知识恢复率 75.81%；必要条件缺失 1，仍未达标，K4 暂缓。当前规则/选择策略 V1.6、prompt V5_SOURCE_ADMISSION、schema V2、最终准入 V4。旧 R5 首轮 35/40 次的报告、标签与成绩继续保留。

此前 [F1/F2/F3 程序修复](quality-v2/v5-deterministic-repair-v1/README.md) 及 [剩余准入修复](quality-v2/v5-admission-repair-v2/README.md) 已通过 112 项 Java、5 项评分器测试与 303 份原文检查；本轮另通过 2 项出站预检后执行模型。实际试跑发现配套步骤缺失与命名故事误保护，下一步优先补修这两项；旧审计与冻结前状态留档。

R3 接口和消费规范见 [初次实现说明](quality-v2/r3-implementation.md) 及当前修复报告，输出协议仍为 [selection-v2.schema.json](quality-v2/selection-v2.schema.json)。当前只读核验使用 `python scripts/run_readme_v5_pilot.py --verify`；下文早期 smoke、旧 schema、旧数量与复跑结果属于历史基线，不能覆盖已执行的冻结请求。

此前 [扩大分类质量验收](quality-v1/README.md) 使用当时授权中的 18/20 次，尚未达标。下文两次 smoke、缓存回放及旧请求规划数量为 V1 历史基线；各轮请求、缓存和额度独立绑定版本，不互相套用。

最终禁网回归共 42 项：41 项通过，1 项真实调用用例按条件跳过。真实调用用例此前已单独执行两次请求；其成功响应还在独立禁网 JVM 中验证了缓存回放，没有新增出站。

## 本轮真实验证结果

| 样本 | 新请求 | 结果 |
|---|---:|---|
| alphacep/vosk-api | 1 | 收到模型返回及 usage，但输出未通过校验；没有采用任何选择，原文继续 REVIEW |
| konvajs/konva | 1 | 4 条选择通过校验：3 KEEP、1 DROP；其余批次因本轮上限未执行 |

提供方返回的 usage 合计 4077 Token；实际入账结果以提供方为准。Vosk 首次记录只保存了失败状态和用量，没有保留无效返回的具体字段，因此本轮不能进一步断言是哪一字段导致拒绝。之后已补充固定失败原因码并以离线非法输出测试覆盖；没有追加真实重试。

旧 50 条账本保持原样，当前授权账本由 38/40 追加为 **40/40**，原 38 条逐字节前缀保持。回执见 [live-budget-receipt.json](live-budget-receipt.json)。额度已经用满，未进行全量模型分类。

结果明细、原文和采用状态见 [live-review.md](live-review.md)，最终测试和缓存回放证据见 [verification.json](verification.json)。

## 调用关系

```text
ReadmeKnowledgeFilter.preview
  → K2 原文块与 REVIEW 队列
  → ReadmeModelReview.plan：分配目标块、章节、相邻说明与限制
  → 缓存校验 / 上下文预算
  → ReadmeReviewJournal：请求前持久保留次数和 Token 预算
  → ReadmeSpringAiReviewTransport → Spring AI ChatModel
  → 严格校验输出
  → 原文选择结果 / 失败时保留 REVIEW
  → ReadmeKnowledgeFilter.reassemble：重建引用依赖和候选包
```

`Source`、原文位置和 K2 已明确的 KEEP/DROP 都不能被模型修改。模型只返回目标 blockId、decision、kind、scope 和枚举 reason；没有改写正文、补全事实或自由文本原因的字段。模型返回的分类与范围是判断提示，原文仍是证据，不能据此自动生成经过验证的业务属性。

## 哪些内容可以交给模型

允许 K2 原因为 `SEMANTIC_REVIEW_NEEDED`、`DEPENDENCY_NOT_APPROVED`、`STRUCTURE_REQUIRES_SEMANTIC_REVIEW` 的 REVIEW 块。截断、未读图片、图片说明、时效不明、仅有未核对的内部目录锚点等继续隔离。

本轮 38 份样本包含 582 个 REVIEW 块：383 个符合语义复核入口，其中 375 个能装入当前请求预算，8 个连同必要上下文过大继续暂缓；199 个保持硬隔离。这里是**请求规划覆盖率**，不代表已调用 375 次或这些块已通过模型。

默认每批最多 4 个目标块，并附带原文章节、相邻说明和显式限制。目标和上下文分开，模型不能对上下文 ID 提交判断。超预算保留完整原文并送审，不剪掉限制来换取请求成功。

## 校验与失败回落

输出协议见 [selection.schema.json](selection.schema.json)，Java 负责执行对应枚举、字段和条件检查，并补充动态规则：

- schema 版本错误、未知/重复/缺少 blockId、额外字段、非字符串枚举、重复 JSON 键、尾随文本：整批不采用。
- provider finishReason 不是 `stop`、响应过大、调用失败或超时：保留 REVIEW，不缓存为成功。
- KEEP 必须有具体知识类型和范围；入口类型只能作为入口元数据。
- DROP 与原文许可、限制、资源或前置条件冲突：整批回落 REVIEW。
- 代码说明被模型删除时，代码不能单独放行；合并后再次执行原文、来源和上下文依赖校验。
- 模型无工具执行入口，README 中的命令、AGENTS 引用和指令只作为来源数据。

这验证的是输出合法性、引用与保护约束，不能保证模型的语义判断绝对正确。后续还需要人工复核和检索评估。

## 缓存、预算与账本

缓存键包含来源身份和原文、目标与上下文、解析/规则/模型选择策略版本、提示词、schema、模型身份和输出上限。环境验证的模型身份还含提供方配置的单向摘要；不存储密钥原文。只有校验成功的响应原子写入缓存，读取时重新检查身份、摘要和 schema。

跨进程回放发现并修正了 `Map.of` 字段顺序不稳定导致的缓存失效：缓存请求 JSON 现在使用固定键序列化。真实成功响应的旧记录保留，在测试夹具中先核对原请求和新请求的 JSON 结构、系统规则与响应摘要完全相同，再迁移到稳定键；原始验证回执没有被改写。普通缓存仍要求精确键匹配，不会随意搜索并套用其他来源的结果。

调用前通过文件锁追加 START，失败和进程异常后都不返还本次预算。相同 runId 重启继续累计；重试也占次数，最多允许 1 次显式重试。本轮真实验证配置为不重试。另有总输入预算＋输出预留的累计上限。

输入预算使用**UTF-8 字节数＋固定消息开销的保守代理**，不是提供方真实 tokenizer；实际 usage 有返回时另外记录，没有则保持未知。当前单请求代理上限 12000、输出上限 2048。Spring AI 适配器复用现有 HTTP 驱动的连接/读取超时和原有验收额度限制；在线验证每次超时上限约 35 秒。

K3 账本记录 runId、缓存键、次数预留、状态及已知 usage，不记录密钥或提示词原文。模拟测试使用临时目录并标记 simulated，与真实请求账本分开。

## 如何本地复验

```powershell
cd D:\Orangejd
.\scripts\run_readme_recommendation_checks.ps1
```

此命令使用已有 Java 21/Maven 缓存并禁用容器网络，执行 R3 准入、依赖回归及原文完整性检查。输出为 `_experiment_staging/readme-recommendation-v3` 和 [R3 验证](quality-v2/recommendation-verification.json)；[R2 依赖验证](quality-v2/dependency-verification.json) 及旧结果保留。

原 `run_readme_model_review.ps1` 对应历史 K3 入口，会写入旧 staging 路径；当前复验使用上面的独立输出入口。新脚本对照旧 K1 标签时显式传入旧授权追加回执，并保持旧预览和评分不变。

原 K1 的 38 条账本哈希基线保留，不应改成 40 条来掩盖历史变化；统计工具会校验追加回执和旧记录前缀。

`run_readme_model_review_live.py` 是本轮两次真实验证的受限执行器，仅允许从原有账本的 38/40 开始；不提供新额度，已有结果保留后拒绝重复执行。生产批量处理需要后续明确预算与任务入口，不能通过换账本或 runId 绕过全局授权。

## 代码阅读顺序

1. `ReadmeModelReview.plan`：为何需要原文块、章节和限制，怎样处理大上下文。
2. `ReadmeModelReview.validate`：模型输出为什么不能直接采用。
3. `ReadmeModelReview.review`：缓存、尝试次数、失败回落和原文合并。
4. `ReadmeReviewJournal.reserve`：文件锁、持久次数和 Token 预留。
5. `ReadmeSpringAiReviewTransport`：把选择器连接到现有 Spring AI ChatModel。
6. `ReadmeModelReviewTest`：非法输出、超时、缓存失效、保护冲突、并发额度等失败场景。

这些类位于 `orange-server/src/main/java/com/orangejd/server/ragb/ingest/`。没有新增框架依赖或 Python 服务。

## 后续工作

2026-10-30 最新：[V5 实测结果](quality-v2/v5-pilot-v3/findings.md) 已封存 16 首轮＋2 失败预留，共 18/18 次。5 仓库同集可用知识从 40/62 增至 47/62，新增 9 条、退化 2 条；仍有一处必要步骤缺失，一致率与恢复率未达标。下一步修非代码配套依赖与命名故事误保护；只读复核用 `python scripts/run_readme_v5_pilot.py --verify`。本轮不再调用模型，K4 暂缓。

真实验证的结果、预算回执和当前实现哈希见本目录验收文件。两次请求只验证少量样本的调用和选择流程，不能替代 38 份全量模型分类或独立质量评估。

K4 需要将过滤结果持久化为派生版本，并统一目录、关键词、向量和最终证据的代际与可见性。K5 再以新样本和固定配置比较知识保留率、噪声率、Recall/MRR、回答依据、延迟与成本。

K4 应消费合并后的 `Result.preview` 候选包，不能直接把 `selections` 当成已批准知识：选择可能被最后的依赖校验继续降为 REVIEW。持久化时还要显式保存 K3 的 policy/prompt/schema/model 身份和预算配置；`preview.policyVersion` 本身仍表示 K2 规则版本。

R3 已让推荐适用性、归属和输出去向随 `Result.recommendation` 的候选传递。K4 应持久化这个规范对象和每块元数据；先完成 R4 冻结与 R5 统一质量验收，不能仅凭原始 Block KEEP 绕过准入约束。
