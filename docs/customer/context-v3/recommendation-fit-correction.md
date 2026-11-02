# 推荐用途与初学者适配修正

本轮已完成代码修正、离线回归、真实目录探针及旧错误结果的 HTTP 导入复核。**尚未完成修正后的真实模型推荐复测，整体仍不标记为发布验收通过。** 上轮 LIVE-06、LIVE-08 的失败记录保留，见 [真实验收复盘](live-acceptance-review.md)。

## 修正了什么

### 1. 具体用途不能被“没有硬条件冲突”代替

原问题：用户要商品、采购、销售、库存功能，这些需求被保存为 SOFT；即使全部 UNKNOWN，只要没有 HARD 冲突，也可能显示 VERIFIED_PROJECTS。

现在分别处理四个维度：

| 维度 | 核验方式 | 对结果的影响 |
| --- | --- | --- |
| 具体用途 | topic 与具体业务 goal 均需要项目自身原文支持 | 没有现成功能依据，不能显示已核实满足或导入 |
| 必要条件 | 保留用户明确的 HARD 条件及其当前版本 | 冲突排除；未知在严格模式排除，探索模式标待核实 |
| 一般目标 | 学习、练习、求职等用于偏好排序 | 不当作项目功能，也不当作学习适配证明 |
| 学习适配 | 用户基础＋目录难度＋仓库规模，明确说明依据有限 | 存在门槛或未知时，整体为 PROJECTS_WITH_LIMITATIONS |

没有把用户 SOFT 改写为 HARD。“要给出已满足用途的结论，必须有用途依据”属于结论门槛；用户仍可明确接受探索或改造路线。

需求匹配输出新增 capabilityScope：EXISTING / ADAPTATION / UNKNOWN。具体用途只有 EXISTING 才能进入 SATISFIED；“可视化构建器、数据库连接、通用框架可以搭建库存系统”不能证明该项目已有库存功能。正反结论保留原文片段、来源版本和需求版本，并经过第二次语义核验。两次调用仍可能有相关误判，不能视为绝对正确。

未核实的用途在探索模式保留为候选，明确显示限制、禁用导入；严格模式排除。服务端重新计算当前具体用途和必要条件是否满足，不再依赖 HARD 列表为空时的 allMatch 结果。没有有效待核验要求也不会凭空获得完整已核实标签。

### 2. 初学者排序不能只信“入门”标签

统一识别“Java初学者、初学、新手、刚学、零基础、beginner、novice”等原话，识别常见否定形式。保留 experience 原文，不自动增加专区或难度硬条件。语言、资源限制等明确要求继续优先过滤。

原排序先偏向“结构化字段完整＋已有证据”，弱化了学习适配。现在在用途相关性之后考虑学习适配，再比较条件完整度、证据及评分。进阶项目保留为带门槛说明的备选，不按仓库名字排除。

真实目录探针发现 **Nacos、Doris 都被现有数据标为“入门”，同时 codebaseSize=LARGE**。因此又补充了 SCOPE_UNVERIFIED：大型仓库未取得具体新手示例或子模块的适配依据时，不认定适合初学者，降低排序并说明限制。前端徽标显示“门槛待核对”，原目录难度仍可查看；数据库中的原始标签没有被改写。

较小或未知规模项目的“入门”标签也只作为初步排序线索，不承诺两周完成或适合零基础。学习适配仍是偏好维度：若具体用途和必要条件确实满足，带学习门槛的项目可由用户自行决定是否导入；整体结果仍标限制。

### 3. 历史错误结果不能绕过新门槛

导入接口复核当前任务、需求版本、具体用途/必要条件、证据活动版本及仓库身份。旧卡片即使保存着 importable=true，也必须重新通过当前规则。语义用途证据与最终引用均检查版本，过期资料要求重新推荐。

## 代码阅读路径

| 文件 | 职责 |
| --- | --- |
| CustomerRecommendationPolicy.java | 定义用途、条件、偏好及已核实结论门槛 |
| CustomerLearningFit.java | 基础识别、难度/规模限制和排序依据 |
| CustomerProjects.java | 保留原过滤，接入学习适配排序和推荐理由 |
| CustomerRequirementMatcher.java | 分项目、分需求核验原文与现成功能；汇总限制 |
| CustomerRecommendationSteps.java | 最终回答区分已核实用途和待考察候选 |
| CustomerTaskService.java | 将具体用途未知纳入候选驱动的追问依据 |
| CustomerImportService.java | 导入时独立复核，阻断旧结果绕过 |
| ProjectRecommendationCard.vue | 展示用途核验、学习门槛与待核实标签 |

Java 文件目录为 orange-server/src/main/java/com/orangejd/server/customer；Vue 文件位于 orange-web/src/components。

现有 LangGraph4j 编排、Spring AI 适配、Redis VectorStore 与上下文工程继续复用。语义匹配仍遵守最多 3 个项目、12 对需求检查及既有输入/时间预算，放不下或未核验的要求保持 UNKNOWN。具体用途现在会进入匹配及必要的二次核验，可能增加模型调用与耗时，修正后的端到端性能尚未实测。

## 本轮验证

| 检查 | 结果 | 边界 |
| --- | --- | --- |
| 隔离网络的后端相关回归 | 535 项：512 通过、23 条件跳过、0 失败/错误 | 模型使用固定响应，不代表真实推荐准确率 |
| 前端测试与构建 | 23 项通过，构建成功 | 构建保留已有工具链告警 |
| 真实目录排序探针 | 527 个目录项目；Nacos、Doris 不再进入本探针前 3 项，均标 SCOPE_UNVERIFIED | 未运行语义召回或模型，不是完整客服推荐 |
| 旧 ToolJet 结果导入 | 实际 HTTP 409，提示具体用途或必要条件依据不足 | 使用原失败运行及当前任务，没有新建推荐 |
| 后端恢复 | 本地 18080，db/redis/rag_vector_store 均 UP | 最终状态见 verification JSON |
| 出站额度 | 本轮修正新增 0 次；新账本仍 38/40，原账本仍 50 条且哈希不变 | 剩余 2 次不足以完成完整推荐场景复测 |

关键回归覆盖：SOFT 用途未知、通用构建能力冒充现成功能、二次核验拒绝、正例完整支持、部分功能、严格模式、明确冲突、普通练习目标、初学者变体/否定、实际 select 排序、硬语言约束、目录难度缺失、LARGE＋入门的矛盾、旧结果导入拒绝和正例导入保留。

证据：

- [最终回归](recommendation-fit-regression.json)
- [真实 HTTP 导入复核](recommendation-fit-http-check.json)，可用 scripts/customer_recommendation_fit_http_check.py 重跑，不调用模型。
- [真实目录探针](recommendation-fit-catalog-probe.json)；[补充规模保护前的结果](recommendation-fit-catalog-before-scope-check.json)保留，说明为什么第一次排序修正仍不充分。
- [最终验证状态](recommendation-fit-verification.json)

## 仍未验收通过的部分

真实目录探索范围共 296 项，其中主语言已记录且满足 Java 的只有 17 项，279 项缺少主语言；277 项缺少难度。这些未知项在探索模式可出现，但必须继续携带条件缺口，不可当作已经确认的 Java 入门项目。

本次无语义召回的目录前 3 项为 processing/processing4、ciembor/agent-rules-books、embabel/embabel-agent-examples。其中第二项语言未知，第三项仍有学习门槛。这只证明新保护与目录排序实际生效，**不能据此判定推荐名单已适用**。资料类仓库识别、项目级事实质量与新手实践范围仍需后续数据核对；没有直接批量修正现有目录。

LIVE-QUALITY-01、LIVE-QUALITY-02 当前状态为“代码及固定样本已修，真实推荐复测待完成”。后续须用足够且明确授权的额度，重跑原初学者/库存请求、真实功能正例、严格模式和否定约束，联合检查最终回答、卡片、需求匹配与导入资格；不能只看协议 SUCCEEDED 或有引用就判通过。releaseApproved 继续为 false。

源码修改前快照：_staging/recommendation-fit-20261029-161401。所有修改仅作用于本地，未部署服务器。
