# K3-R3 推荐适用性、导航隔离与归属实现

日期：2026-10-29。实现位于 `ReadmeRecommendationProjection`，由 `ReadmeModelReview.review()` 在原文重组后统一调用。当前为离线实现与验证阶段；真实分类门槛、生产入库和检索增益分别属于 R5、K4、K5。

## 已实现的数据流

```text
ReadmeKnowledgeFilter.preview：原文结构与内容决定
  → ReadmeModelReview.plan：语义待判块 + 缺少推荐适用性判断的规则 KEEP
  → 原文依赖成组 / 预算 / 缓存 / 严格协议校验
  → ReadmeKnowledgeFilter.reassemble：保持原文、固定规则与结构依赖
  → ReadmeRecommendationProjection.project
      内容判断 + 推荐适用性 + 产品/版别/平台归属
      → 必要条件及依赖检查
      → 正文候选 / 导航 / 归档 / 待复核
  → Result.preview：只含通过准入的候选包
  → Result.recommendation：候选的适用性、归属、证据与版本
```

规则 KEEP 说明内容本身被规则保护，不自动代表适合推荐。对尚无明确适用性依据的 KEEP，模型可以补分类或报告不确定，不能把固定 KEEP 改成 DROP。原文 Block 保留原始决定；最终是否可用以派生 Assessment 和实际候选包为准。

## 结果对象与消费约定

| 对象 | 实际字段 / 行为 |
|---|---|
| Selection | 原有 blockId、decision、kind、scope、reason，新增 applicability、evidenceBlockIds；只引用本次输入中提供的块，必须包含当前块 ID |
| Assessment | proposedDecision、effectiveDecision、kind、applicability、scopes、evidenceBlockIds、dependencyBlockIds、disposition、reason |
| Candidate | packet、subjectScopes、evidence；同一正文候选的 primary 具有相同归属集合；上下文每块也携带自身归属 |
| Pointer | label、href、evidenceBlockId；保存原始标签和地址，不请求目的页面 |
| Projection | 全块 assessments、knowledgeCandidates、pointerCandidates、pointers、reviewBlockIds 以及 versions |
| Versions | parser、rulePolicy、selectionPolicy、prompt、schema、modelIdentity、runId；与调用账本可关联 |

K4 的规范输入应为 **Result.recommendation 的 Candidate 与 Assessment**，把准入包、每块归属和证据映射一起持久化。Result.preview 的候选已经过滤，可作兼容预览；只读取 preview.blocks 中的 KEEP 或模型 selections，会绕过最终适用性及依赖结果。

`scopes` 可同时保留 ENTERPRISE、PLATFORM_SPECIFIC、VERSION_SPECIFIC 等条件；具体条件文字仍以 evidenceBlockIds 指向的原文为准。RELATED_PRODUCT 不归并为 CORE；社区与企业混在未拆原文块时送审。没有新增产品名称、平台支持或许可证事实的自由改写字段。

## 规则行为

- **项目范围**：有明确项目用途/源码用途原文时可作适用性依据；混合仓库中的可运行模块单独处理。课程行政内容、课程大纲和资源集合描述不作为项目能力。仅见技术标题但用途不足时待判，不自动归 CORE。
- **课程与证书**：考试资格、证书展示/撤销等保留原文并排除推荐用途；TLS certificates 等技术内容不按课程行政规则排除。规则覆盖有限，不声称理解所有课程表达。
- **导航**：纯标签链接仅输出 Pointer 和导航 Candidate，正文候选与上下文均不包含导航；许可证/条件链接标记未读取知识缺口，不能推导已经核验许可或资源要求。导航不继承大正文及全局保护项的组包预算。
- **含链接正文**：旧规则曾标为 SOURCE_POINTER 的完整说明段落需要再次分类，不能仅因有链接整段当作导航。模型判断为正文后仍执行所有准入约束。
- **限制保护**：模型将项目限制标成 OUT_OF_SCOPE 时，如果没有课程/资源等明确排除依据，改为 UNKNOWN 并保留复核；相关正文不能借此绕过限制。真正范围外课程行政内容不会作为全局约束混入项目代码。
- **依赖**：代码的说明、命令和恢复步骤继续共同判断。必要依赖在导航、归档或待复核中时不发布相关正文。
- **预算与故障**：超时、非法协议、未执行、超预算和不确定仍不能入正文。没有生成预算内候选包的 KEEP 明确转为派生待复核；不能靠模型 KEEP 充当知识恢复。

## 协议与缓存

实际版本：

- 规则：`README_RULE_PREVIEW_V1_3`
- 模型选择：`README_MODEL_REVIEW_V1_3`
- 提示词：`README_SELECTOR_ZH_V4_RECOMMENDATION`
- 输出：`README_SELECTIONS_V2`
- 最终准入：`README_RECOMMENDATION_V1`

新协议见 [selection-v2.schema.json](selection-v2.schema.json)。Java 还检查动态 ID 集合、证据自引用、重复和遗漏、保护冲突、原文映射与候选元数据一致性。导航或范围外内容可以没有明确产品范围，正文 IN_SCOPE 的 KEEP 必须有具体类型与 scope。

缓存键包含新准入策略及各版本，旧响应不会按缺失字段自动补默认值。缓存命中后仍重新校验并执行最终准入。仍使用原请求上限、文件锁账本、Token 预留和失败计数，不引入新的外部工具或调用通道。

## 验证与复跑

```powershell
cd D:\Orangejd
.\scripts\run_readme_recommendation_checks.ps1
```

此入口使用禁网 Docker 与已有 Maven 缓存。R2 语料回归也输出到新的 `_experiment_staging/readme-recommendation-v3/dependency`，不重写 R2 的历史验收回执。

仅复核已完成结果：`python scripts/verify_readme_recommendation.py`。机器证据写入 [recommendation-verification.json](recommendation-verification.json)，代表性原文与派生输出保存在 `_experiment_staging/readme-recommendation-v3/examples.json`。

验证包括模拟模型输出的准入边界、真实冻结原文 303 份的映射与候选完整性、协议 schema、缓存和失败保护。模拟判断与规则预览不能用于声称真实分类精确率达到 95%，也不能证明推荐或检索已经变好。

## 后续边界

R4 要在本版本稳定后冻结新标注集、实际批次、提供方配置与预算安排；语义分类规则仍可能漏判、过度送审。当前旧新增授权仍为 18/20，本轮离线操作不占真实请求；新版数据集不能直接套用旧授权文件。

R5 按已确认的 95% / 85% / 85% 门槛统计最终候选与遗漏，并单列导航、范围排除及规则控制。K4 之后才统一数据库、目录、关键词、向量和证据消费者的可见性。
