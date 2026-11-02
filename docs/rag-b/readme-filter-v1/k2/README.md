# K2：Java 离线知识过滤预览

K2 已实现 Java 规则过滤核心、离线运行入口、逐块预览与 K1 标注对照工具。它输出可回溯的知识候选、入口元数据和复核队列；**尚未接入生产入库、模型分类或活动检索索引**。

## 已实现的处理路径

最终回归：**24 项定向测试通过，38 份样本的原文映射和来源隔离检查通过**。对照 175 条 K1 标注：

| K1 预期 | 条数 | 最终规则结果 |
|---|---:|---|
| 应保留 | 128 | 85 条保留，43 条送复核，0 条错误删除 |
| 应排除 | 29 | 23 条排除，6 条送复核，0 条直接保留 |
| 应复核 | 18 | 18 条进入复核，0 条直接放行 |

62 条涉及许可、限制、兼容、部署或维护的 KEEP 中，有 19 条仍待复核。**没有误删不代表关键知识已经全部可用于推荐。** 程序检查到的映射错误、跨来源引用、非 KEEP 块进入候选包均为 0。

全部正文解析成 2514 个原文块，其中 KEEP 745、DROP 1187、REVIEW 582；DROP 包含空白及布局，不能把 1187 当作语义噪声数量。知识候选包 142 个、入口候选包 79 个，均为离线产物。未新增模型或 Embedding 请求，原有两份出站账本哈希保持一致。

```text
冻结的 README 正文＋来源身份
  → 解析标题、段落、完整代码、表格、独立链接与布局
  → KEEP / DROP / REVIEW 规则判断
  → 绑定章节、代码前置说明及原文限制
  → 知识候选包 / 文档入口包 / 复核原文
  → 原文 UTF-16 回溯和来源隔离校验
  → JSON 与 Markdown 离线预览
```

实现要点：

- 每个原文字符都归入一个块，记录来源、版本、原文哈希、行号及 Java UTF-16 半开区间；未改写原文或虚构事实。
- 完整代码保持原样；不闭合围栏、截断 import 和相关介绍句进入复核。代码前置说明未获准时，代码也暂停放行。
- Markdown 表格保留表头与否定值，残缺尾行单独复核。HTML 警告表受到保护，翻译导航表可排除。
- 独立徽章分块，区分许可证/维护信息、CI/下载量和运行入口。图片内容未读取时，不把 alt 或说明文字当成能力证明。
- 候选按章节组合，附上标题和限制原文；临时操作与恢复步骤可一起呈现。多个原文区间通过 blockId 列表引用，不伪装成连续引用。
- 复核块不能进入候选包。大于 64000 UTF-16 单元的候选章节暂缓输出并标记，原文完整保留；不默默截断。
- 仓库角色和知识类型是规则提示，不是已确认业务事实。没有自动生成用户画像、项目难度、完成周期或资源要求。

这不是完整 CommonMark 解析器，也不是最终 BGE-M3 Token 分块器。复杂行内 HTML、模糊产品边界、跨段语义依赖等仍需要复核。当前按章节打包和附带限制是保守的离线预览策略；接入模型前仍需补充上下文 Token 预算。

## 文件与学习顺序

| 文件 | 职责 |
|---|---|
| `orange-server/src/main/java/com/orangejd/server/ragb/ingest/ReadmeKnowledgeFilter.java` | 纯 Java 核心；先看 `preview`，再看 `parse`、`decide`、`verify` |
| `orange-server/src/main/java/com/orangejd/server/ragb/ingest/ReadmeFilterOfflineCli.java` | 读取本地快照、选择样本、输出预览；不启动 Spring |
| `orange-server/src/test/java/com/orangejd/server/ragb/ingest/ReadmeKnowledgeFilterTest.java` | 人工构造的边界回归，覆盖 Unicode、代码、表格、限制、来源隔离等 |
| `orange-server/src/test/java/com/orangejd/server/ragb/ingest/ReadmeKnowledgeFilterCorpusTest.java` | 显式启用的冻结样本验收，默认普通测试时跳过 |
| `scripts/run_readme_filter_preview.ps1` | 禁用容器网络，使用已有 Java 21/Maven 缓存运行定向测试与预览 |
| `scripts/evaluate_readme_filter_preview.py` | 独立读取 K1 标注做离线统计，不被 Java 过滤算法调用 |

没有新增 Maven 依赖。Python 只用于本地验收统计，Java 核心运行不依赖 Python。

## 本地运行

需要 Docker 已启动、本地已有 `maven:3.9.9-eclipse-temurin-21` 镜像和 Maven 缓存；本轮已使用这些环境跑通。命令不启动后端、不连接 MySQL/Redis，也不读取 `.env.local`。

在 PowerShell 中：

```powershell
cd D:\Orangejd
.\scripts\run_readme_filter_preview.ps1 -Split all
python scripts/evaluate_readme_filter_preview.py --split all
```

K3 已在原有授权内追加最后 2 次真实验证请求，账本由 38/40 变为 40/40。此后运行对照统计时，显式附带回执以验证历史前缀，而不是改写 K1 的 38 条基线：

```powershell
python scripts/evaluate_readme_filter_preview.py --split all --ledger-receipt _experiment_staging/readme-filter-k3/live-budget-receipt.json
```

输出位置：

- `_experiment_staging/readme-filter-k2/all-preview.md`：全部样本的逐块原文、决定、规则和位置。
- `_experiment_staging/readme-filter-k2/all-preview.json`：结构块、引用依赖、知识候选与入口候选。
- `_experiment_staging/readme-filter-k2/all-summary.json`：总体分块计数。
- 本目录 [all-evaluation.md](all-evaluation.md)：按仓库展示标注对照和分歧。
- 本目录 [all-evaluation.json](all-evaluation.json)：完整对照数据和账本核验。

也可以使用 `-Split development` / `-Split validation` 只输出相应子集；统计脚本的 `--split` 参数须一致。重新运行会更新相应预览与统计，不会更改冻结原文或 K1 标注。

## 评估过程与口径

首先只用开发集 24 份调整规则，冻结 V1 后执行预留 14 份。首次验证发现两个问题：免费模型调用额度被误保留、社群邀请未被充分过滤。原始结果保存在 [first-validation-evaluation.md](first-validation-evaluation.md) 和对应 JSON；初次实现哈希保存在 `first-implementation-freeze.json`。

随后修正“免费 calls/requests/tokens 的时效判断”和“社群章节及图片路径不能冒充文档标签”的通用规则，并增加回归用例；当前策略为 `README_RULE_PREVIEW_V1_1`。**这 14 份已参与修复，最终 38 份属于回归集合，不再宣称独立验证。** 下一次独立质量评估需要新样本。

对照只统计 K1 已标注范围，不将未标注正文自动算作预测正确。完整非空白 UTF-16 覆盖数保留在 JSON 中，同时区分语义内容与格式：

- KEEP 的“错误删除”不计纯布局标签/空白；原字符仍在归档块中，可回溯。
- REVIEW 的“错误放行”不把独立章节标题当成知识事实；标题仍可作为上下文记录。
- “待复核”不等于保留成功；“没有删除”不等于可以进入知识索引。
- 候选包只含 KEEP 块是程序一致性校验；是否误把不确定内容判成 KEEP 是另一项质量指标，分别报告。

最终数字和测试证据见 [verification.json](verification.json)。这些结果不能代替独立人工评审、检索 Recall/MRR、端到端回答质量或在线延迟测试。

## 当前边界与下一步

K2 的价值是让过滤过程可观察、可回归，先避免明显噪声和错引。仍有不少 K1 应保留内容被保守送审，不能直接拿规则结果替换全库。

K3 应处理这些复核块：向模型提供原文块 ID、必要标题和限制，仅允许返回选择与分类；补齐 schema 校验、缓存、次数/Token 限制和失败回落。调用前继续遵守现有额度与账本。随后 K4 才接入独立索引代际，并统一目录、关键词、向量和证据读取口径。
