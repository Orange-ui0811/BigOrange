# 过滤知识分块与原文结构分块：独立检索验证

同一批 5 个冻结 README、固定本地 BGE-M3、Redis HNSW＋正文关键词＋名称召回，复用 Java 分词与 RRF 评分。每题每组运行 3 次；没有模型回答、查询改写或额外重排模型。

28 题：22 道有批准证据，6 道证据不足。问题和预期证据由助手标注，样本已用于调试；不能当成独立人工评测或网站回答准确率。

| 方案 | 项目命中@1 | 项目命中@3 | 项目命中@5 | 直接证据完整@5 | 回查后证据完整@5 | 检索P50 / P95 ms |
|---|---:|---:|---:|---:|---:|---:|
| RAW_STRUCT | 21/22 | 22/22 | 22/22 | 16/22 | 16/22 | 9.3 / 17.3 |
| KNOWLEDGE_V2 | 22/22 | 22/22 | 22/22 | 16/22 | 16/22 | 4.4 / 9.2 |

本轮本地 Embedding 推理请求 44 次，包含预热与入库；云端调用 0。单题本地 Embedding P50 / P95：54.3 / 79.6 ms。

检索耗时使用预先计算的相同查询向量，仅包含三路召回与融合；不包含 Embedding、回答生成、业务校验和网络传输到网页。查询 Embedding 单独测量，不能把检索时间当成完整客服响应时间。

证据不足的问题仍可能返回相似候选。此处未接入客服拒答／追问节点，不能宣称已正确拒答；`evaluation.json` 单独列出这些候选。

原文组包含完整冻结原文；知识组仅含批准知识与必要上下文，且加入检索前缀。因此这是过滤＋分块＋上下文整体方案对照，不能将全部差异归因于分块长度。

项目命中按仓库文档身份统计，本轮没有核验业务 projectId 映射、在线状态和导入资格。未知配置与许可不由模型补写。

逐题诊断：

| 题目 | 方案 | 首位项目 | 项目@5 | 直接／回查证据完整 |
|---|---|---|---|---|
| R01 | RAW_STRUCT | 240 | True | True / True |
| R02 | RAW_STRUCT | 244 | True | True / True |
| R03 | RAW_STRUCT | 244 | True | True / True |
| R04 | RAW_STRUCT | 244 | True | True / True |
| R05 | RAW_STRUCT | 244 | True | True / True |
| R06 | RAW_STRUCT | 244 | True | False / False |
| R07 | RAW_STRUCT | 244 | True | False / False |
| R08 | RAW_STRUCT | 244 | True | False / False |
| R09 | RAW_STRUCT | 522 | True | True / True |
| R10 | RAW_STRUCT | 522 | True | False / False |
| R11 | RAW_STRUCT | 546 | True | True / True |
| R12 | RAW_STRUCT | 546 | True | True / True |
| R13 | RAW_STRUCT | 546 | True | True / True |
| R14 | RAW_STRUCT | 546 | True | True / True |
| R15 | RAW_STRUCT | 546 | True | True / True |
| R16 | RAW_STRUCT | 546 | True | False / False |
| R17 | RAW_STRUCT | 546 | True | True / True |
| R18 | RAW_STRUCT | 546 | True | False / False |
| R19 | RAW_STRUCT | 244 | False | False / False |
| R20 | RAW_STRUCT | 244 | False | False / False |
| R21 | RAW_STRUCT | 427 | False | False / False |
| R22 | RAW_STRUCT | 240 | False | False / False |
| R23 | RAW_STRUCT | 522 | False | False / False |
| R24 | RAW_STRUCT | 522 | False | False / False |
| R25 | RAW_STRUCT | 546 | True | True / True |
| R26 | RAW_STRUCT | 522 | True | True / True |
| R27 | RAW_STRUCT | 427 | True | True / True |
| R28 | RAW_STRUCT | 546 | True | True / True |
| R01 | KNOWLEDGE_V2 | 240 | True | True / True |
| R02 | KNOWLEDGE_V2 | 244 | True | True / True |
| R03 | KNOWLEDGE_V2 | 244 | True | True / True |
| R04 | KNOWLEDGE_V2 | 244 | True | True / True |
| R05 | KNOWLEDGE_V2 | 244 | True | False / False |
| R06 | KNOWLEDGE_V2 | 244 | True | False / False |
| R07 | KNOWLEDGE_V2 | 244 | True | True / True |
| R08 | KNOWLEDGE_V2 | 244 | True | False / False |
| R09 | KNOWLEDGE_V2 | 522 | True | True / True |
| R10 | KNOWLEDGE_V2 | 522 | True | True / True |
| R11 | KNOWLEDGE_V2 | 546 | True | True / True |
| R12 | KNOWLEDGE_V2 | 546 | True | True / True |
| R13 | KNOWLEDGE_V2 | 546 | True | True / True |
| R14 | KNOWLEDGE_V2 | 546 | True | False / False |
| R15 | KNOWLEDGE_V2 | 546 | True | True / True |
| R16 | KNOWLEDGE_V2 | 546 | True | False / False |
| R17 | KNOWLEDGE_V2 | 546 | True | True / True |
| R18 | KNOWLEDGE_V2 | 546 | True | False / False |
| R19 | KNOWLEDGE_V2 | 244 | False | False / False |
| R20 | KNOWLEDGE_V2 | 244 | False | False / False |
| R21 | KNOWLEDGE_V2 | 244 | False | False / False |
| R22 | KNOWLEDGE_V2 | 240 | False | False / False |
| R23 | KNOWLEDGE_V2 | 244 | False | False / False |
| R24 | KNOWLEDGE_V2 | 522 | False | False / False |
| R25 | KNOWLEDGE_V2 | 546 | True | True / True |
| R26 | KNOWLEDGE_V2 | 522 | True | True / True |
| R27 | KNOWLEDGE_V2 | 244 | True | True / True |
| R28 | KNOWLEDGE_V2 | 546 | True | True / True |
