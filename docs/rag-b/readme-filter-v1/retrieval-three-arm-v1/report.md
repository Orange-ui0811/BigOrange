# README三组纯向量检索实验

A＝完整原文＋B版结构切块；B＝共同批准原文＋同一结构切块；C＝相同批准原文＋知识单元切块。三组均无派生前缀、关键词或名称加分。

真实样本是已有5仓库，构造样本6份独立报告；助手标注、已知来源，不是独立人工评测。原文有答案但未准入计入遗漏。C算法是封存生产代码的实验副本，仅改类名与前缀返回值，不修改正式链路。

| 样本 | 组别 | K | 完整证据：直接 | 直接2048预算 | 完整单元恢复2048预算 |
|---|---|---:|---:|---:|---:|
| REAL | A | 1 | 20/35 | 20/35 | 20/35 |
| REAL | A | 3 | 31/35 | 31/35 | 31/35 |
| REAL | A | 5 | 32/35 | 32/35 | 32/35 |
| REAL | A | 10 | 34/35 | 33/35 | 33/35 |
| REAL | B | 1 | 12/35 | 12/35 | 12/35 |
| REAL | B | 3 | 19/35 | 19/35 | 19/35 |
| REAL | B | 5 | 20/35 | 20/35 | 20/35 |
| REAL | B | 10 | 23/35 | 23/35 | 23/35 |
| REAL | C | 1 | 18/35 | 18/35 | 18/35 |
| REAL | C | 3 | 25/35 | 25/35 | 25/35 |
| REAL | C | 5 | 28/35 | 28/35 | 28/35 |
| REAL | C | 10 | 29/35 | 29/35 | 29/35 |
| SYNTHETIC | A | 1 | 5/12 | 5/12 | 5/12 |
| SYNTHETIC | A | 3 | 11/12 | 11/12 | 11/12 |
| SYNTHETIC | A | 5 | 12/12 | 12/12 | 12/12 |
| SYNTHETIC | A | 10 | 12/12 | 12/12 | 12/12 |
| SYNTHETIC | B | 1 | 3/12 | 3/12 | 3/12 |
| SYNTHETIC | B | 3 | 10/12 | 10/12 | 10/12 |
| SYNTHETIC | B | 5 | 11/12 | 11/12 | 11/12 |
| SYNTHETIC | B | 10 | 12/12 | 12/12 | 12/12 |
| SYNTHETIC | C | 1 | 6/12 | 6/12 | 9/12 |
| SYNTHETIC | C | 3 | 7/12 | 7/12 | 9/12 |
| SYNTHETIC | C | 5 | 9/12 | 9/12 | 9/12 |
| SYNTHETIC | C | 10 | 9/12 | 9/12 | 9/12 |

## Top5、2048预算的逐题胜负

| 样本 | 对比 | 改善题 | 退步题 |
|---|---|---|---|
| REAL | A→B | R28 | R03: RANKING_MISS; R04: RANKING_MISS; R11: RANKING_MISS; R12: RANKING_MISS; R14: RANKING_MISS; R17: RANKING_MISS; R20: ADMISSION_GAP; R29: ADMISSION_GAP; R30: ADMISSION_GAP; R31: ADMISSION_GAP; R34: RANKING_MISS; R35: ADMISSION_GAP; R36: ADMISSION_GAP |
| REAL | B→C | R03, R04, R07, R11, R14, R17, R34, R38 | 无 |
| REAL | A→C | R07, R28, R38 | R12: RANKING_MISS; R20: ADMISSION_GAP; R29: ADMISSION_GAP; R30: ADMISSION_GAP; R31: ADMISSION_GAP; R35: ADMISSION_GAP; R36: ADMISSION_GAP |
| SYNTHETIC | A→B | 无 | S2-Q1: RANKING_MISS |
| SYNTHETIC | B→C | 无 | S6-Q1: CHUNKING_GAP; S6-Q2: CHUNKING_GAP |
| SYNTHETIC | A→C | 无 | S2-Q1: CHUNKING_GAP; S6-Q1: CHUNKING_GAP; S6-Q2: CHUNKING_GAP |

ADMISSION_GAP表示原文事实未进入共同准入集合；CHUNKING_GAP表示已准入但分块没有提供；RANKING_MISS表示已入库但未进入当前TopK；BUDGET表示本可召回但完整片段超预算。准入缺口还可能来自产品范围限制，不能仅凭本评分断定分类错误。

新方案若只在已准入题上改善、全来源可答题却退步，应优先检查知识供给；C相对B的退步则检查分块边界及原子超限。真实与构造样本的结果不合并，也不能用较少返回内容制造Token收益。

## 逐题Top5诊断

| 题目 | 来源可答 | A | B | C |
|---|---|---|---|---|
| R01 想要可以与文档对话、支持多用户的 AI 应用。 | True | COMPLETE | COMPLETE | COMPLETE |
| R02 Flowise 安装需要哪个版本的 Node.js？ | True | COMPLETE | COMPLETE | COMPLETE |
| R03 Flowise 如何通过 npm 安装并启动？ | True | COMPLETE | RANKING_MISS | COMPLETE |
| R04 Flowise 本地 Docker 镜像怎么构建和运行？ | True | COMPLETE | RANKING_MISS | COMPLETE |
| R05 Flowise Docker Compose 配置环境文件后如何启动？ | True | COMPLETE | COMPLETE | COMPLETE |
| R06 Flowise 后端和前端分别用什么技术，第三方节点在哪个模块？ | True | COMPLETE | COMPLETE | COMPLETE |
| R07 Flowise 源码如何安装依赖并构建？ | True | RANKING_MISS | RANKING_MISS | COMPLETE |
| R08 Flowise 构建报错 134，Windows PowerShell 怎么调大 Node 堆内存？ | True | COMPLETE | COMPLETE | COMPLETE |
| R09 Appsmith 可以搭建管理后台和仪表盘吗？ | True | COMPLETE | COMPLETE | COMPLETE |
| R10 Appsmith 使用什么开源协议？ | True | COMPLETE | COMPLETE | COMPLETE |
| R11 Docguard 是做什么的？ | True | COMPLETE | RANKING_MISS | COMPLETE |
| R12 Docguard 有外部依赖吗？ | True | COMPLETE | RANKING_MISS | RANKING_MISS |
| R13 Docguard 构建需要什么 Go 版本，怎么构建？ | True | COMPLETE | COMPLETE | COMPLETE |
| R14 Docguard 如何让 CI 对 medium 级别告警失败？ | True | COMPLETE | RANKING_MISS | COMPLETE |
| R15 Docguard 默认扫描哪些文件扩展名？ | True | COMPLETE | COMPLETE | COMPLETE |
| R16 Docguard 如何并发扫描，默认有多少 worker？ | True | COMPLETE | COMPLETE | COMPLETE |
| R17 Docguard 是否保证检测到的都是攻击？ | True | COMPLETE | RANKING_MISS | COMPLETE |
| R18 Docguard 支持输出 JSON 吗，怎么使用？ | True | COMPLETE | COMPLETE | COMPLETE |
| R19 Flowise 需要多少内存才能生产运行？ | False | SOURCE_UNANSWERABLE | SOURCE_UNANSWERABLE | SOURCE_UNANSWERABLE |
| R20 Flowise 的 VITE_PORT 开发环境怎么配置？ | True | COMPLETE | ADMISSION_GAP | ADMISSION_GAP |
| R21 n8n 社区版与企业版分别允许什么商业用途？ | False | SOURCE_UNANSWERABLE | SOURCE_UNANSWERABLE | SOURCE_UNANSWERABLE |
| R22 AnythingLLM 有没有明确的最低 CPU 与内存要求？ | False | SOURCE_UNANSWERABLE | SOURCE_UNANSWERABLE | SOURCE_UNANSWERABLE |
| R23 能推荐一个 Java Spring Boot 两周完成的商城吗？ | False | SOURCE_UNANSWERABLE | SOURCE_UNANSWERABLE | SOURCE_UNANSWERABLE |
| R24 Appsmith 的单点登录是免费社区版自带的吗？ | False | SOURCE_UNANSWERABLE | SOURCE_UNANSWERABLE | SOURCE_UNANSWERABLE |
| R25 想找一个 Go 写的命令行工具，预先扫描知识库中的隐藏提示注入。 | True | COMPLETE | COMPLETE | COMPLETE |
| R26 想要一个开源低代码平台搭建企业内部管理后台。 | True | COMPLETE | COMPLETE | COMPLETE |
| R27 想找一个前端 React、后端 Node 并能扩展第三方节点的工具。 | True | COMPLETE | COMPLETE | COMPLETE |
| R28 有没有能够在知识入库前接入 CI 的静态扫描工具？ | True | RANKING_MISS | COMPLETE | COMPLETE |
| R29 n8n用npx试运行的命令和前置依赖是什么？ | True | COMPLETE | ADMISSION_GAP | ADMISSION_GAP |
| R30 n8n的Docker运行如何保存数据卷？ | True | COMPLETE | ADMISSION_GAP | ADMISSION_GAP |
| R31 n8n本地编辑器使用哪个地址和端口？ | True | COMPLETE | ADMISSION_GAP | ADMISSION_GAP |
| R32 Flowise的Docker Compose启动前如何准备环境文件？ | True | COMPLETE | COMPLETE | COMPLETE |
| R33 如何停止Flowise本地Docker镜像？ | True | COMPLETE | COMPLETE | COMPLETE |
| R34 Flowise源码构建后用什么命令启动？ | True | COMPLETE | RANKING_MISS | COMPLETE |
| R35 Appsmith README列出了哪两种开始使用的方式？ | True | COMPLETE | ADMISSION_GAP | ADMISSION_GAP |
| R36 README中单独介绍的Appsmith Agents是什么产品？ | True | COMPLETE | ADMISSION_GAP | ADMISSION_GAP |
| R37 AnythingLLM的介绍明确提到了哪些定制和多用户能力？ | True | COMPLETE | COMPLETE | COMPLETE |
| R38 Docguard列出了哪些Go静态检查和并发测试命令？ | True | RANKING_MISS | RANKING_MISS | COMPLETE |
| R39 Docguard如何只检查Markdown和HTML文件？ | True | COMPLETE | COMPLETE | COMPLETE |
| R40 Docguard默认什么级别告警会导致退出码1，其他情况是什么退出码？ | True | COMPLETE | COMPLETE | COMPLETE |
| S1-Q1 导出仓库记录需要什么权限？ | True | COMPLETE | COMPLETE | COMPLETE |
| S1-Q2 该应用支持Windows吗？ | True | COMPLETE | COMPLETE | COMPLETE |
| S2-Q1 部署前要设置什么隔离变量，启动命令是什么？ | True | COMPLETE | RANKING_MISS | CHUNKING_GAP |
| S2-Q2 隔离服务的Java版本和操作系统要求是什么？ | True | COMPLETE | COMPLETE | COMPLETE |
| S3-Q1 region-02所需内存和适用版别是什么？ | True | COMPLETE | COMPLETE | COMPLETE |
| S3-Q2 region-60所需内存、系统与运行时是什么？ | True | COMPLETE | COMPLETE | COMPLETE |
| S4-Q1 社区版包含审计导出吗？ | True | COMPLETE | COMPLETE | COMPLETE |
| S4-Q2 region-30的运行要求属于哪个版别？ | True | COMPLETE | COMPLETE | COMPLETE |
| S5-Q1 如何只演练迁移而不实际修改数据？ | True | COMPLETE | COMPLETE | COMPLETE |
| S5-Q2 启动只读服务的命令及前置环境是什么？ | True | COMPLETE | COMPLETE | COMPLETE |
| S6-Q1 完整脚本最后如何执行严格校验？ | True | COMPLETE | COMPLETE | CHUNKING_GAP |
| S6-Q2 该脚本最终发布到哪里，用什么命令？ | True | COMPLETE | COMPLETE | CHUNKING_GAP |

本轮本地Embedding实际请求36次，云端分类／聊天请求0；每题共享查询向量，各组真实Redis查询3次。未连接业务MySQL或切换网站索引。

预算按完整片段Token数之和计，同一C父单元只计一次；超限跳过并记录，不从TopK外补取。原子代码无法切块是C的供给缺口，保留在分母。

本轮检验当前512/768策略的相对表现，不证明最优参数；没有生成回答、业务资格或全库可用性结论。完整数值与失败归因见evaluation.json；稳定性与向量精确排名核对见live-completion.json。

复验：python scripts/readme_three_arm.py verify。
