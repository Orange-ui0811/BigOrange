# 推荐系统技术说明

代码核对基线：`e4eb584b`（2026-11-02）。本文是推荐模块的统一技术入口；实现、参数和路由变化时应同步更新本文。发生冲突时以源码为准，实验结果见 [评测与验收](RECOMMENDATION_EVALUATION.md)。代码合入 main 不表示生产已启用。

## 1. 四条路径与职责

| 路径 | 用户入口 | 主要链路 | 输出与边界 |
|---|---|---|---|
| 新手项目目录 | `GET /app/beginner-projects`、`/weekly`、`/{id}` | GitHub 快照 → BeginnerProjectScorer → 富化与 BeginnerEditorialPolicy → 发布/周选 → BeginnerProjectService、Mapper | 技术分、编辑分、难度、依赖、说明；目录排序不是用户行为训练模型 |
| 职业成长目录 | `GET /app/career-projects`、`/weekly`、`/{id}` | GitHub 快照＋岗位样本统计 → CareerProjectScorer＋CareerRoleTaxonomy → 发布/周选 → CareerProjectService、Mapper | 岗位分、综合分、能力证明及项目资料；不是录用概率 |
| 趋势推荐 A | `POST /app/trend/analyze-v2` | TrendAnalysisService → TrendV2Analyzer → recommendVerified＋TrendContextBuilder → TrendPromptFactory/TrendModelClient → TrendResultAssembler/TrendEvidenceBinder | 图谱分析、真实候选、生成建议、来源及检索追踪；RAG 可选 |
| Customer Agent B | `POST /app/customer/sessions/{id}/runs` | CustomerStore/CustomerRuns → 执行引擎 → CustomerConversation → CustomerTools → B 检索＋CustomerProjects → 证据/需求核验 | 会话、追问、比较、修改需求、至多 3 个项目、逐项依据及待核实项 |

用户目录接口不是 Scorer 的直接调用入口。规则分主要在采集/刷新时计算并保存；查询时读取已发布资料。旧的 `recommend(...)` 与趋势使用的 `recommendVerified(...)` 不能混为一条算法。

## 2. 新手项目：技术评分、编辑评分与查询

权威代码：[BeginnerProjectScorer](../orange-server/src/main/java/com/orangejd/server/service/BeginnerProjectScorer.java)、[BeginnerEditorialPolicy](../orange-server/src/main/java/com/orangejd/server/service/BeginnerEditorialPolicy.java)、[服务](../orange-server/src/main/java/com/orangejd/server/service/BeginnerProjectService.java)、[SQL](../orange-server/src/main/resources/mapper/BeginnerProjectMapper.xml)。

### 2.1 `beginner-v3` 技术分

输入为 `RepositorySnapshot`，输出 `ScoreResult(score,difficulty,status,evidence)`。先相加，再截断至 0–100：

| 项 | 当前规则 |
|---|---|
| 基础 | +35 |
| 主语言 | TypeScript/JavaScript/HTML/CSS/Python +15，其他 +4 |
| README | 有 +15，无 −35 |
| 活跃度 | 最近更新 ≤14 天 +15，≤45 天 +10，≤120 天 +5，更早 −10；未知 0 |
| Stars | ≥100 +10，≥20 +6，其余 +2 |
| 包管理器 | 检测到 +5，未检测到 +1 |
| 快速开始 | 检测到 +10，否则 −20 |
| 许可元数据 | 非空且不是 NOASSERTION +5，否则 −10 |
| 环境惩罚 | Docker −12、数据库 −15、密钥 −12、外部服务 −8、原生构建 −8、大仓库 −8，累加 |

归档、缺 README 或要求 GPU 时最终技术分直接为 0、状态 INVALID。否则为 CANDIDATE；难度按分数 ≥80 为 BEGINNER、≥65 为 EASY，其余 MEDIUM。上述是启发式信号：未检测到依赖不证明没有依赖；许可字段存在不等于已核实商业使用资格。

### 2.2 编辑分与发布资格

富化产生/人工维护的五项评分先经 `calibrate` 校准，每项 1–5；技术分、依赖数量、快速开始、大仓库和纯基础设施等信号会限制乐观评分。

```text
editorialScore = (30×vibeCodingFit + 25×showcaseValue
                +15×extensionPotential +15×scopeClarity +15×contentPotential) / 5
```

实现为整数运算。`publishable` 要求技术分 ≥65、编辑分 ≥76、五个维度各 ≥4；详情和具体发布操作仍由 Service 校验。编辑分不是 `beginner-v3`，也不是纯确定性仓库元数据分。

公开列表先执行 SQL 的 `publicFilters`，按编辑分、技术分、GitHub 更新时间降序及仓库名升序分页；周选有独立资格、排序与人工/算法来源。旧 `recommend` 最多返回 5 个，空结果会放宽语言、再放宽全部筛选；趋势的 verified 路径不会调用这条回退。

## 3. 职业成长：`career-v1`

权威代码：[CareerProjectScorer](../orange-server/src/main/java/com/orangejd/server/service/CareerProjectScorer.java)、[岗位分类](../orange-server/src/main/java/com/orangejd/server/service/CareerRoleTaxonomy.java)、[服务](../orange-server/src/main/java/com/orangejd/server/service/CareerProjectService.java)、[SQL](../orange-server/src/main/resources/mapper/CareerProjectMapper.xml)。

输入为仓库快照及每类已批准岗位样本数。对每个岗位类别分别计算五项，各限制在 0–20：

| 维度 | 原始计算 |
|---|---|
| 岗位相关性 | `4 + min(12,关键词命中数×3) + min(4,岗位样本数/2)`，样本数除法为整数 |
| 能力证明 | `3 + 测试?7:0 + CI?4:0 + 发布自动化?3:0 + (stars≥100?3:1)`；无测试时最多 12 |
| 技术覆盖 | `4 + min(8,技术栈项数×2) + 数据库?2:0 + Docker?2:0 + 外部服务?2:0 + 架构文档?2:0` |
| 作品完整性 | README 4、快速开始 5、部署说明 4、非空许可 2、90 天内更新 3、非大仓库 2；无快速开始最多 12 |
| 面试价值 | 基础 4、架构文档 5、测试 3、CI 2、数据库或 Docker 3、中/大规模 3（其余 1）、有开放 issue 2 |

表中 `条件?分数:0` 表示条件成立才加分。岗位总分是五项之和；岗位按总分降序、代码升序排列。设第一、第二名为 t1/t2，其余岗位均分为 rest：综合分为 `clamp(round(0.7×t1 + 0.2×t2 + 0.1×rest),0,100)`；不足三个岗位时 rest 使用第二名，不足两个时第二名使用第一名。

归档或缺 README 标为 INVALID，但与新手技术分不同，此处不会强制综合分归零。GPU、原生构建或大仓库对应 ADVANCED；否则两项以上重依赖或中等规模对应 INTERMEDIATE，其余 ENTRY。

公开列表通过 `publicFilters` 联查新手仓库状态，按综合分、仓库更新时间降序及仓库名升序。旧 `recommend` 在至多 100 条目录记录上按词项适配、综合分和仓库名排序；趋势使用 verified 版本，在至多 200 条公开记录中进行下述确定性匹配。

## 4. 趋势推荐 A：显式画像、目录匹配和来源绑定

主要代码：[TrendV2Analyzer](../orange-server/src/main/java/com/orangejd/server/service/TrendV2Analyzer.java)、[TrendCandidateMatcher](../orange-server/src/main/java/com/orangejd/server/service/TrendCandidateMatcher.java)、[TrendContextBuilder](../orange-server/src/main/java/com/orangejd/server/service/TrendContextBuilder.java)、[TrendRetrievalSupport](../orange-server/src/main/java/com/orangejd/server/service/TrendRetrievalSupport.java)、[TrendEvidenceBinder](../orange-server/src/main/java/com/orangejd/server/service/TrendEvidenceBinder.java)。

输入包括 topics/customTopics、languages、purpose/customPurpose、experience、timeCommitment、useRag、usePersona 和目录偏好。画像是显式输入/已确认信息，不是点击行为学习的隐向量。

### 4.1 目录候选

学习/新手意图选择新手目录，求职/就业/面试意图选择职业目录，两者均无时同时选择；每类 `MATCH_LIMIT=3`。

`TrendCandidateMatcher.score = 40×命中的主题组数 + 语言命中奖励15`。主题使用确定性别名组，拉丁别名有边界检查。有主题但一组也未命中时返回 −1 并排除；多个主题不要求全部命中。语言未命中仅不加分，不能称为严格语言过滤。

新手 verified：匹配分降序 → 编辑分降序 → 技术分降序 → 仓库名升序。职业 verified：匹配分降序 → 综合分降序 → 仓库名升序。它们不回退到无关热门项目，词项匹配本身仍不证明具体业务功能满足。

### 4.2 A 检索和生成

`useRag=false` 不检索；无 VectorStore 返回 UNAVAILABLE。当前 `retrieve(input)` 不只是单次向量检索：多条查询各取 Top50、相似度阈值 0.55，另有正文词法路线，经过主题门禁并按文档片段去重融合：

```text
片段分 = Σ routeWeight / (20 + 路线内排名)
routeWeight：首条向量查询0.55，其余向量查询0.25，词法0.20
排序分 = 片段分 × (1 + MANUAL奖励0.05 + min(0.20,来源偏好奖励))
```

排名从 1 开始；来源偏好通常为显式目录偏好 0.20、学习/求职匹配 0.05，否则 0。保留有可核对仓库地址或手工项目映射的片段，按项目去重，最多 3 项；岗位片段最多 1 项。单路线失败仍可使用其他路线结果，状态与耗时进入 retrievalTrace。不要把文件内旧的简单问答辅助检索参数当作此推荐路径参数。

LLM 接收图谱、用户输入、目录候选及证据卡片，生成分析和建议。Binder 通过候选 ID/规范化 URL 绑定来源并处理不受支持项；它不等同客服的逐需求原文核验。模型生成的 score 不是规则质量分。图谱、模型失败及额度退回由 Analyzer/计费层处理，不应包装成成功推荐。

## 5. Customer Agent B：文档排序与项目排序是两层

代码入口：[CustomerRuns](../orange-server/src/main/java/com/orangejd/server/customer/CustomerRuns.java)、[CustomerConversation](../orange-server/src/main/java/com/orangejd/server/customer/CustomerConversation.java)、[推荐步骤](../orange-server/src/main/java/com/orangejd/server/customer/CustomerRecommendationSteps.java)、[CustomerProjects](../orange-server/src/main/java/com/orangejd/server/customer/CustomerProjects.java)、[需求核验](../orange-server/src/main/java/com/orangejd/server/customer/CustomerRequirementMatcher.java)。

```text
登录与归属校验、请求去重、并发/截止时间
→ 加载任务与上下文 → 指代消解 → 理解意图 → 合并需求
→ 按意图选择直接回答、追问、问答、详情/比较或推荐
→ 推荐：查询计划 → B 候选召回 → 用途审查 → 选择项目
→ 限定项目范围取证 → 生成带引用陈述 → 引用/事实核验
→ 需求逐项匹配 → 建议与结果 → 消息、用量、事件、检查点
```

LEGACY 和 LANGGRAPH4J 复用业务节点；并非每轮经过所有节点。记忆按会话/任务隔离，包含结构化需求、关键原话、最近消息和按需摘要；信息不足可受控回查。数据库任务续接不等同任意中断点恢复完整执行。

### 5.1 B 召回、融合及重排

[RagBSearchService](../orange-server/src/main/java/com/orangejd/server/ragb/service/RagBSearchService.java) 通过 [RagBIndexRouter](../orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBIndexRouter.java) 读取活动 generation，区分 EXACT 与 HNSW_HYBRID。

客服工具要求 bge-m3、1024 维、`STRUCTURE_M3_512_768_64_V1` 和 HNSW_HYBRID。B 正文使用结构切块：目标 512、上限 768 BGE Token、章内重叠 64；Embedding 支持 Gitee 或固定 digest 的本地 Ollama。通用 A 模型默认 bge-small-zh-v1.5，代码维度回退为 512；更换模型不能继续混用旧向量。

HNSW_HYBRID 分别获取向量、正文词法和名称候选，每路默认上限 128，融合候选默认 80。**实际正文评分使用 [RagBProjectEvidenceScorer](../orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBProjectEvidenceScorer.java)**：

```text
R(r) = r>0 ? 1/(60+r) : 0
正文融合分 = R(向量排名) + 0.2×R(词法排名) + (明确点名 ? 0.1 : 0)
若向量排名和词法排名均不存在，正文融合分为0
```

名称独立命中不能单独成为回答证据。[RagBFusionScorer](../orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBFusionScorer.java) 还定义了 `R(vector)+0.2R(lexical)+点名时[3R(name)+0.1]`，并提供名称判断，但不能把这个完整公式误写成当前 HNSW 正文排序。EXACT 是另一个向量扫描后端，不套用 HNSW 的融合公式。

服务层顺序为：活动版本/删除状态/业务来源/请求范围/结构化约束过滤 → 可选来源偏好 → 原分数排序去重 → Reranker → 项目多样性及 TopK → 相关性与价格证据门禁 → 证据分组/回答策略。若调用者传入来源偏好，混合路线加 0.002，其他路线加 0.03；不能自动当作每个客服请求都传入了该偏好。

[CustomerTools](../orange-server/src/main/java/com/orangejd/server/customer/CustomerTools.java) 的普通问答取 Top8；项目召回 Top20 并要求多样性；项目取证 Top20 且限定选定项目的文档 ID。阈值均为 0.55，但它是召回阈值，不是最终 RRF/重排分数阈值，也不是“满足要求概率”。客服没有把全部开放需求直接传入 Redis 条件：具体要求还在用途审查和项目匹配层校验。

[RagBReranker](../orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBReranker.java) 默认关闭，启用后使用配置模型 `bge-reranker-v2-m3`，最多 80 项、每批 20、整轮共享 5 秒、最多并发 2、无 HTTP 重试。仅全部批次完整有效才应用新顺序；超时、异常响应、无凭据、繁忙或输入超限整体回退原排序。`score` 保留融合/偏好分，`rerankScore` 独立存储。重排没有创造事实或补齐候选池外知识。

### 5.2 项目资格、排序与得分

候选池含已发布的新手、职业目录，以及有有效 GitHub 文档身份的外部项目。已知冲突、归档/无效身份、排除反馈和纯资源集合会影响准入。结构化条件包括语言、难度、周期上下限、来源及是否允许 Docker/数据库/密钥/硬件；具体处理见 `accepts`、`explorable`。普通探索可保留部分未知并标注；STRICT 要求更严格，未知不等于满足。

项目排序是**分层比较**，不是只按 total：

1. 用途匹配优先级 `CustomerPurpose.rank`；
2. 初学者适配顺序（ENTRY_LEVEL、CAUTION、未知、SCOPE_UNVERIFIED、CHALLENGING）；
3. CONFIRMED 优先，再看是否有证据；
4. 综合分降序；同分按 sourceRef 升序；
5. 规范化仓库身份去重，最多返回 3 个。后续核验可能继续排除，因此最终可能少于 3 个。

`ranking.version=customer-rank-v4-learning-fit`：

```text
total = semantic + quality + preference + goalPreference - uncertaintyPenalty
semantic = 检索命中该项目 ? 45/sqrt(去重项目在检索结果中的位置) : 0
quality = clamp(目录质量字段,0,100)×0.15；缺失为0
```

新手项目 quality 取编辑分，职业项目取综合分；不要统一称为新手技术分。对明确初学者，目录难度入门 +30、进阶 −40、中等 −8、其他 −10；另加 `clamp(beginnerScore,0,100)×0.1`（字段存在时），大仓库 −15、学习范围未核实 −25。其他用户不加这些初学者项。

学习目标且来源为新手 +15；职业目标且来源为职业 +15。未知字段惩罚：语言 20，其他列入 `unknownFields` 的字段各 8。当前列表涉及语言、难度、周期上下限和禁止的环境依赖；评分代码虽保留主题 12 分分支，但 `unknownFields` 未添加 topic，不能将它写成当前实际触发的主题惩罚。开放自然语言需求不自动进入此公式，total 也没有被截断为 0–100。

### 5.3 证据、追问和失败边界

需求匹配输出 SATISFIED/PARTIAL/CONFLICT/UNKNOWN，使用项目自身原文和结构化事实。现成功能与“可以二次开发实现”分开；用途或硬要求未完整核实不能授予已验证导入资格。[CustomerImportService](../orange-server/src/main/java/com/orangejd/server/customer/CustomerImportService.java) 在导入前再次核对归属、任务/需求版本、来源身份及活动证据版本。

用户需求缺失或冲突可追问；缺少项目事实应补查/标待核实，不能靠用户回答变成已支持。当前有任务状态、问题续接和取舍机制，尚不是经过训练的信息增益/RL 追问策略。

工具有归属、超时、调用次数、截止时间和取消检查；模型预算受阶段限制。异常可降级为待核实、检索不可用或失败状态，不默认用通用大模型生成无证据项目替代。完整协议以控制器、CustomerStore、CustomerRuns 和需求/证据校验源码为准。

## 6. 模型、开关与维护约定

| 项 | 默认值/含义 |
|---|---|
| CUSTOMER_ENABLED / RAG_B_ENABLED / RAG_B_RERANK_ENABLED | 全为 false；代码存在不等于已启用 |
| CUSTOMER_ALL_AUTHENTICATED_USERS | false；白名单用 CUSTOMER_ALLOWED_ACCOUNTS |
| CUSTOMER_EXECUTION_ENGINE | LEGACY；可显式选择 LANGGRAPH4J |
| CUSTOMER_MODEL / CUSTOMER_THINKING | deepseek-flash / false；实际 endpoint/凭据由配置解析 |
| CUSTOMER_RAW_ACCESS_ENABLED | false；开启后仍需管理员鉴权和原因审计 |
| 框架 | Spring AI 1.0.0 模型/检索接口；LangGraph4j 1.8.27 编排；未引入 Python 服务 |

配置权威入口：[application.yml](../orange-server/src/main/resources/application.yml)、[CustomerProperties](../orange-server/src/main/java/com/orangejd/server/customer/CustomerProperties.java)、[RagBProperties](../orange-server/src/main/java/com/orangejd/server/ragb/config/RagBProperties.java)、[RagBRerankProperties](../orange-server/src/main/java/com/orangejd/server/ragb/config/RagBRerankProperties.java)。环境配置和活动 generation 决定实际运行模型，不能从 README 默认值推断服务器已配置。

修改算法时同步：本说明的版本/公式/调用链、对应单元及基础设施测试、评测文档的比较条件。项目质量分、匹配分、RRF、rerankScore 和生成分各有语义，不可直接相加冒充统一概率。协同过滤、行为训练的 Learning-to-Rank、训练双塔和基于收益学习的追问策略均未作为上述路径的已实现算法。
