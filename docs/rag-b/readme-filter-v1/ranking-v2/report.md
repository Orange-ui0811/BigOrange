# 项目内证据排序：固定候选消融验证

同一批 5 仓库、同样 86 个原文块和 28 个知识块、同样 28 题。保留原模型与索引，每次召回的完整候选同时应用三种评分，避免模型或分块变化混入排序效果。

| 分块 | 排序 | 首位项目命中 | 前5块项目命中 | 前5块证据完整 | 检索P50 / P95 ms |
|---|---|---:|---:|---:|---:|
| RAW_STRUCT | LEGACY_RRF | 21/22 | 22/22 | 16/22 | 6.9 / 12.0 |
| RAW_STRUCT | PROJECT_CONTENT | 21/22 | 22/22 | 20/22 | 6.9 / 12.0 |
| RAW_STRUCT | SCOPED_CONTENT | 21/22 | 22/22 | 20/22 | 6.9 / 12.0 |
| KNOWLEDGE_V2 | LEGACY_RRF | 22/22 | 22/22 | 16/22 | 3.6 / 5.1 |
| KNOWLEDGE_V2 | PROJECT_CONTENT | 22/22 | 22/22 | 21/22 | 3.6 / 5.0 |
| KNOWLEDGE_V2 | SCOPED_CONTENT | 22/22 | 22/22 | 21/22 | 3.6 / 5.1 |

LEGACY_RRF 保留原名称排名加分；PROJECT_CONTENT 只保留每个显式匹配项目相同的 0.1 加分，项目内部由向量与正文关键词排序。SCOPED_CONTENT 额外只保留候选中显式匹配的项目，用于实验对照，本轮不将硬范围过滤接入网站。

生产 Java Redis 检索代码已接入 PROJECT_CONTENT_RRF_V1；本轮没有重启网站后端或切换活动索引。直接调用真实生产 Repository 的 Redis 集成测试另外通过，包含过滤与并发检索。

这不是完整客服准确率：Top5 按片段计数；22 道正例按助手冻结原文块的严格完整性标准评分。6 道缺证据题只用于观察返回候选，未实现自动判断全部事实充分，也未生成回答。

本轮复用旧索引向量，正文 Embedding 0 次，仅本地预热与28题查询共29次推理，使用新账本；云端调用0。查询向量保存供后续离线消融。

检索耗时不包含查询 Embedding、模型回答、账号或项目资格校验；只有5仓库，不能推断全库吞吐。旧问题、标签、分类、分块、首轮结果与账本保持原样。

| 题号 | 排序 | 新知识组证据完整 |
|---|---|---|
| R01 | LEGACY_RRF | True |
| R02 | LEGACY_RRF | True |
| R03 | LEGACY_RRF | True |
| R04 | LEGACY_RRF | True |
| R05 | LEGACY_RRF | False |
| R06 | LEGACY_RRF | False |
| R07 | LEGACY_RRF | True |
| R08 | LEGACY_RRF | False |
| R09 | LEGACY_RRF | True |
| R10 | LEGACY_RRF | True |
| R11 | LEGACY_RRF | True |
| R12 | LEGACY_RRF | True |
| R13 | LEGACY_RRF | True |
| R14 | LEGACY_RRF | False |
| R15 | LEGACY_RRF | True |
| R16 | LEGACY_RRF | False |
| R17 | LEGACY_RRF | True |
| R18 | LEGACY_RRF | False |
| R25 | LEGACY_RRF | True |
| R26 | LEGACY_RRF | True |
| R27 | LEGACY_RRF | True |
| R28 | LEGACY_RRF | True |
| R01 | PROJECT_CONTENT | True |
| R02 | PROJECT_CONTENT | True |
| R03 | PROJECT_CONTENT | True |
| R04 | PROJECT_CONTENT | True |
| R05 | PROJECT_CONTENT | True |
| R06 | PROJECT_CONTENT | False |
| R07 | PROJECT_CONTENT | True |
| R08 | PROJECT_CONTENT | True |
| R09 | PROJECT_CONTENT | True |
| R10 | PROJECT_CONTENT | True |
| R11 | PROJECT_CONTENT | True |
| R12 | PROJECT_CONTENT | True |
| R13 | PROJECT_CONTENT | True |
| R14 | PROJECT_CONTENT | True |
| R15 | PROJECT_CONTENT | True |
| R16 | PROJECT_CONTENT | True |
| R17 | PROJECT_CONTENT | True |
| R18 | PROJECT_CONTENT | True |
| R25 | PROJECT_CONTENT | True |
| R26 | PROJECT_CONTENT | True |
| R27 | PROJECT_CONTENT | True |
| R28 | PROJECT_CONTENT | True |
| R01 | SCOPED_CONTENT | True |
| R02 | SCOPED_CONTENT | True |
| R03 | SCOPED_CONTENT | True |
| R04 | SCOPED_CONTENT | True |
| R05 | SCOPED_CONTENT | True |
| R06 | SCOPED_CONTENT | False |
| R07 | SCOPED_CONTENT | True |
| R08 | SCOPED_CONTENT | True |
| R09 | SCOPED_CONTENT | True |
| R10 | SCOPED_CONTENT | True |
| R11 | SCOPED_CONTENT | True |
| R12 | SCOPED_CONTENT | True |
| R13 | SCOPED_CONTENT | True |
| R14 | SCOPED_CONTENT | True |
| R15 | SCOPED_CONTENT | True |
| R16 | SCOPED_CONTENT | True |
| R17 | SCOPED_CONTENT | True |
| R18 | SCOPED_CONTENT | True |
| R25 | SCOPED_CONTENT | True |
| R26 | SCOPED_CONTENT | True |
| R27 | SCOPED_CONTENT | True |
| R28 | SCOPED_CONTENT | True |
