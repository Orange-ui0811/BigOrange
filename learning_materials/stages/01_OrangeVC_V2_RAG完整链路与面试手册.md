先给你一个总定义：

> OrangeVC 的一次 V2 RAG 请求，是“多源知识预入库 + 查询时多路向量召回 + 个性化加权融合 + Prompt 增强生成 + 结构化校验 + 工程治理”的完整链路。

它不是简单的“用户问题丢给大模型”，也不是 Spring AI 一行 Advisor 自动完成。这个项目里：

- Spring AI 主要负责 `EmbeddingModel` 和 `VectorStore` 抽象。
- RAG 编排由业务代码手动完成。
- LLM Chat 请求由 `RestTemplate` 直接调用 OpenAI 兼容接口。
- 外围还有限流、配额、幂等、重试、故障切换和结果持久化。

---

# 一、先建立全局视角：RAG 有两个生命周期

很多面试者只讲在线查询，容易漏掉一半。完整 RAG 分成：

```text
离线/异步知识入库
原始文档 → 清洗 → 分块 → Embedding → 写入向量库

在线请求
用户画像 → 查询改写 → Query Embedding → 向量召回
→ 融合排序 → 拼入 Prompt → LLM 生成 → 校验输出
```

关键认知：

> 知识入库不是每次用户请求时重新做，而是提前完成。在线请求只负责把查询向量化并检索已有向量。

---

# 二、知识是如何进入向量库的

## 1. 知识来源

OrangeVC 的知识来源不止一种：

- 管理员上传的 Markdown/TXT
- GitHub README
- 岗位信息
- 新手友好项目
- 职业成长项目

主要入口在 [DocumentServiceImpl.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/impl/DocumentServiceImpl.java:26)。

GitHub README 会经过：

```text
拉取 README
→ 调用文本模型翻译成中文
→ 分块
→ Embedding
→ 写入 Redis VectorStore
```

新手项目和职业项目则由 [ProjectKnowledgeSyncService.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/ProjectKnowledgeSyncService.java:23) 定期同步。

它会先把数据库中的结构化字段转成类似 Markdown 的知识文档：

```text
# 项目名称

## 知识分类
职业成长项目

## 职业价值
……

## 可证明能力
……

## 面试讨论点
……
```

这样做的意义是把结构化业务数据转换成适合语义检索的自然语言。

## 2. 为什么需要内容哈希

项目同步时计算 SHA-256：

```text
content → SHA-256 → contentHash
```

如果：

- 索引状态已经是 `DONE`
- 内容哈希没有变化
- 已存在知识文档 ID

就不重新向量化。

这样可以避免定时任务每隔几分钟都重复调用 Embedding API，减少成本和无效写入。

你面试时可以称它为：

> 基于内容哈希的增量索引与幂等同步。

## 3. 文档如何分块

[DocumentServiceImpl.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/impl/DocumentServiceImpl.java:28) 中的主要参数是：

```text
CHUNK_SIZE = 500
OVERLAP = 50
MAX_CHUNKS = 100
MAX_CHUNK_CHARS = 400
```

基本过程：

1. 从当前 `start` 向后取约 500 个字符。
2. 尽量回退到最近的换行符，避免从段落中间切断。
3. 下一块从 `end - 50` 开始，产生 50 字符重叠。
4. 清除纯格式符号后，过滤过短的无意义块。
5. 最终单块又被截断到最多 400 字符。

这里有一个需要主动看出来的细节：

> 名义切块大小是 500，但清洗后单块最多保留 400 字符，所以真正进入向量库的有效上限是 400。

### 为什么要分块

如果整篇 README 直接生成一个向量：

- 一个向量需要同时表达安装、功能、架构、API 等多个主题；
- 用户只问其中一个小问题时，整篇文档的语义可能不够集中；
- 召回后放入 Prompt 的内容太长。

分块可以提高语义粒度。

### 为什么要重叠

假设某段关键内容刚好跨越两个 Chunk 边界，没有重叠时，上下文会被截断。

重叠可以保护跨边界语义，但代价是：

- 重复内容增加；
- 向量数量增加；
- 召回结果可能出现近似重复；
- Embedding 和存储成本增加。

## 4. Metadata 存了什么

每个向量文档附带：

- `docId`
- `docTitle`
- `chunkIndex`
- `source`
- `sourceRef`
- `catalog`
- `projectId`

例如：

```text
向量：
[0.012, -0.083, ...]

文本：
“该项目适合希望学习 Agent 工作流的 Java 开发者……”

metadata：
{
  docId: "123",
  docTitle: "某 Agent 项目",
  chunkIndex: 2,
  source: "CAREER_PROJECT",
  projectId: "45"
}
```

Metadata 的作用包括：

- 知道检索结果来自哪篇文档；
- 删除或更新文档时定位所有 Chunk；
- 去重；
- 区分普通知识、新手项目和职业项目；
- 后续支持 metadata filtering。

## 5. Embedding 是在哪里发生的

业务代码调用：

```java
vectorStore.add(documents)
```

但它没有手工逐个计算向量。Spring AI 的 Redis VectorStore 会通过注入的 `EmbeddingModel` 计算向量。

项目自己实现了 [GiteeAiEmbeddingModel.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/ai/GiteeAiEmbeddingModel.java:27)，其行为是：

```text
文本列表
→ POST /v1/embeddings
→ 指定 bge-small-zh-v1.5
→ 读取返回的 embedding 数组
→ 转换为 float[]
```

模型声明输出 512 维向量。

因此，入库时实际发生的是：

```text
Chunk 文本
→ bge-small-zh-v1.5
→ 512 维向量
→ Redis Stack
```

查询时也一样：

```text
查询文本
→ 同一个 Embedding 模型
→ 512 维查询向量
→ 与库中向量进行相似度搜索
```

必须使用相同或兼容的 Embedding 模型，否则新查询向量和旧文档向量不处于同一个语义空间。

---

# 三、一次在线 RAG 请求的完整过程

以用户输入为例：

```json
{
  "topics": ["AI智能体", "RAG检索增强"],
  "purpose": "应对求职面试",
  "experience": "新手入门",
  "languages": ["Python"],
  "timeCommitment": "每周5-15小时",
  "useRag": true,
  "usePersona": false,
  "preferBeginnerProjects": true,
  "preferCareerProjects": true
}
```

## 第 0 步：前端确认和幂等键

前端入口在 [useTrendAnalysis.js](D:/Orangejd/orange-web/src/composables/useTrendAnalysis.js:1)。

提交前会：

1. 获取当前 AI 定价。
2. 向用户展示配额确认。
3. 调用 `/app/trend/analyze-v2`。
4. 设置无限前端等待时间，因为模型可能运行较久。

请求拦截器 [request.js](D:/Orangejd/orange-web/src/api/request.js:49) 会给付费 AI 请求添加：

```http
Idempotency-Key: <UUID>
```

网络超时后，前端会保留原来的 Key；请求明确成功或明确失败后再清理。

目的不是提高模型效果，而是避免：

> 用户因为网络超时重新点击，导致重复扣费。

## 第 1 步：Controller 接收请求

入口是 [AppTrendController.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/controller/app/AppTrendController.java:45)。

主要做四件事：

1. 从 `App-Token` 尝试恢复用户身份。
2. 校验至少有一个 Topic，或者开启了画像分析。
3. 使用 Redis 按 IP 做 10 秒限流。
4. 把 DTO 转换成参数 Map，交给 `TrendProfileAnalysisService`。

这里还没有进行向量检索，也没有调用 LLM。

## 第 2 步：业务参数校验

[TrendProfileAnalysisService.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/TrendProfileAnalysisService.java:52) 会再次校验：

- Topic 是否存在；
- 未使用画像时，目的、经验、时间是否完整；
- 文本是否超长；
- List 与字符串是否正确规范化。

这是“边界层校验 + 业务层校验”两层防御。

之后调用 `TrendAnalysisService.analyzeV2()`。

## 第 3 步：生成请求身份

[TrendAnalysisService.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/TrendAnalysisService.java:53) 将参数封装为 `TrendAnalysisInput`，然后创建 `QuotaRequestIdentity`。

[QuotaRequestIdentity.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/QuotaRequestIdentity.java:26) 会：

1. 将请求参数递归排序，生成稳定 JSON。
2. 对请求内容计算 `requestHash`。
3. 对客户端传入的 Idempotency-Key 计算哈希。
4. 根据业务范围、账号和幂等键生成确定性的 `requestId`。

关系可以理解为：

```text
requestHash = SHA256(规范化后的请求参数)

requestId = SHA256(
    业务范围
    + 账号
    + Idempotency-Key 的哈希
)
```

为什么既需要 `requestId` 又需要 `requestHash`？

- `requestId` 判断是不是同一次业务请求；
- `requestHash` 防止客户端错误地用同一个幂等键提交不同参数。

## 第 4 步：进入 AI 调用治理层

请求进入 [AiToolRouter.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolRouter.java:49)。

它首先查询：

```text
这个 requestId + accountId + requestHash
是否已经存在成功结果？
```

如果存在，直接返回保存的结果，不调用模型。

如果不存在：

1. 获取全局并发租约；
2. 尝试获取一个可用的 Gitee Key 槽位；
3. 如果所有 Gitee 槽位都不可用，使用 DeepSeek 兜底配置；
4. 记录调用日志；
5. 每 30 秒续约；
6. 执行业务模型调用；
7. 成功后持久化结果并释放租约；
8. 失败则判断是否可以重试。

这里要注意调用顺序：

> AI Router 先取得调用容量，然后 `TrendV2Analyzer` 才扣配额并执行 RAG。

## 第 5 步：扣除配额

[TrendV2Analyzer.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/TrendV2Analyzer.java:54) 首先调用 `charge()`。

底层 [QuotaServiceImpl.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/impl/QuotaServiceImpl.java:34) 会：

- 锁定用户账户；
- 查询是否已有相同请求操作；
- 有则验证账号、Scope 和请求 Hash；
- 没有则扣减余额；
- 保存不可重复的配额操作和流水。

如果 AI Router 自动重试，同一个请求再次进入 `charge()`，不会再次扣费，而是返回已有消费记录。

## 第 6 步：准备非向量上下文

V2 Analyzer 同时准备三类非 RAG 数据：

1. GitHub Topic 图谱数据；
2. 新手项目结构化候选；
3. 职业项目结构化候选。

新手和职业候选最多各 3 个。

这部分非常重要，因为 OrangeVC 不是只依赖向量检索。最终上下文包含：

```text
用户画像
+ GitHub 真实图谱
+ 向量知识片段
+ 数据库结构化项目候选
```

准确描述应当是：

> 多源上下文增强生成。

不要把它称为“稀疏+稠密混合检索”，因为当前没有 BM25 等稀疏检索。

## 第 7 步：执行 RAG 检索

核心在 [TrendContextBuilder.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/TrendContextBuilder.java:92)。

如果：

- `useRag != true`
- 或者 VectorStore 不存在

直接返回空字符串，不执行检索。

否则根据画像构造四条查询：

```text
1. 技术方向：
   AI智能体 RAG检索增强 开源项目入门实践

2. 学习目的：
   应对求职面试 面试技巧 项目经验 GitHub作品集

3. 编程经验：
   新手入门 学习路径入门

4. 时间投入：
   每周5-15小时 学习计划 时间管理
```

为什么不把所有字段直接拼成一个大查询？

因为用户画像包含多个意图：

- 想学什么；
- 为什么学；
- 当前水平；
- 有多少时间。

单一长查询可能让某个强关键词压制其他维度。拆成四路检索可以扩大召回覆盖面。

每条查询执行：

```java
topK(6)
similarityThreshold(0.55)
```

因此理论上最多得到：

```text
4 × 6 = 24 个候选 Chunk
```

## 第 8 步：去重与融合排序

候选文档通过以下键去重：

```text
docId + chunkIndex
```

如果 metadata 不完整，则使用文本前 50 个字符作为回退。

每次候选出现都会累加：

```text
基础排名分 = 1 / (rank + 1)
总分 = 多路查询排名分之和 + 来源加权
```

例如一份文档在两路查询中分别排第 1 和第 3：

```text
1 / 1 + 1 / 3 = 1.333
```

来源加权：

- 显式优先新手项目：`+0.85`
- 用户是新手或学习目的：`+0.25`
- 显式优先职业项目：`+0.85`
- 用户目的是求职：`+0.25`

最后按累计分数排序，只保留前 4 个 Chunk。

这是一种“类 Reciprocal Rank Fusion”的做法，但不是严格标准 RRF，因为标准 RRF 通常使用：

```text
1 / (k + rank)
```

而当前实现直接使用 `1 / (rank + 1)`，头部结果权重更强。

## 第 9 步：构造增强 Prompt

最终 User Prompt 由 [TrendV2Analyzer.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/TrendV2Analyzer.java:88) 拼成：

```text
## 用户画像
方向、目的、经验、语言、时间
可选的已确认画像

## GitHub 热度数据
Topic、Stars、项目数量、增长率、难度、机会评分

## 知识库参考
片段1：……
片段2：……
片段3：……
片段4：……

## 本次匹配的新手项目
最多3个结构化候选

## 本次匹配的职业成长项目
最多3个结构化候选

请严格按 JSON 格式输出
```

System Prompt 在 [TrendPromptFactory.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/TrendPromptFactory.java:12)，主要定义：

- 模型角色；
- 任务目标；
- JSON 输出结构；
- 字段要求；
- 推荐数量；
- 字段最小内容长度。

V2 设置：

```text
temperature = 0.5
max_tokens = 未显式设置
```

为什么选择较低 Temperature？

因为这是结构化分析任务，需要稳定、可解析，不能过度发散。

## 第 10 步：调用生成模型

[TrendModelClient.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/TrendModelClient.java:16) 直接调用：

```http
POST {endpoint}/chat/completions
Authorization: Bearer ...
Content-Type: application/json
```

请求体包含：

```json
{
  "model": "...",
  "messages": [
    {"role": "system", "content": "..."},
    {"role": "user", "content": "...增强后的上下文..."}
  ],
  "temperature": 0.5
}
```

然后读取：

```text
choices[0].message.content
```

这里面试时要准确说：

> 项目使用了 Spring AI 的 EmbeddingModel 和 VectorStore，但生成模型调用没有使用 Spring AI ChatClient，而是直接调用 OpenAI 兼容接口。

优点：

- 供应商兼容逻辑直接；
- 请求结构完全可控；
- 与自建的多 Key 路由容易结合。

缺点：

- 需要手动处理结构化输出；
- 缺少 ChatClient/Advisor 的统一拦截能力；
- 模型抽象、流式输出和可观测性需要自己实现。

## 第 11 步：解析和验证输出

模型返回的 JSON 字符串进入 [TrendResponseParser.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/TrendResponseParser.java:12)。

处理步骤：

1. 去掉 Markdown JSON 代码块；
2. 使用正则提取 `analysis`；
3. 提取 `recommendations`；
4. 提取 `learning`；
5. 保存原始模型输出；
6. 检查关键字段是否完整。

[TrendResultAssembler.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/TrendResultAssembler.java:14) 会进一步：

- 检查结果完整性；
- 加入余额；
- 加入结构化的新手/职业候选；
- 为候选生成匹配解释；
- 必要时清洗模型返回的引用。

当用户开启“优先新手/职业项目”时，[TrendRecommendationSanitizer.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/TrendRecommendationSanitizer.java:10) 只保留候选集合中真实存在的 URL。

这能够阻止模型凭空生成一部分 GitHub 地址，但不能完全消除内容幻觉。

## 第 12 步：两次结果持久化

这里有两套记录，必须分清。

### 通用 AI 结果记录

AI Router 成功后调用 [AiResultRecordService.java](D:/Orangejd/orange-server/src/main/java/com/orangejd/server/service/AiResultRecordService.java:61)。

用途：

- 幂等结果回放；
- 统一 AI 结果列表；
- 保存业务类型、标题、摘要；
- 结果保存失败时退款。

### 趋势分析历史

返回到 `TrendProfileAnalysisService` 后，再写入 `trend_history`。

用途：

- 保存用户当时填写的画像；
- 保存是否开启 RAG、画像、新手或职业偏好；
- 支持用户重新打开历史分析。

如果趋势历史保存失败：

- AI 结果仍然返回；
- `historySaved=false`；
- 不退款，因为模型分析本身已经成功。

---

# 四、失败场景如何处理

| 失败位置 | 当前行为 |
|---|---|
| VectorStore 不存在 | 跳过 RAG，继续使用画像和图谱 |
| 单路向量查询失败 | 记录日志，继续其他三路查询 |
| 全部向量检索失败 | RAG 返回空，继续非 RAG 生成 |
| 图谱获取失败 | 退款并返回错误 |
| 模型 429/5xx/超时 | AI Router 按策略重试 |
| Key 401/403 | 将对应 Key 标记为无效 |
| Key 429 | 按 Retry-After 暂时限流该槽位 |
| 模型结果不完整 | 视为 AI 返回异常，可进入重试 |
| 通用结果保存失败 | 退款并报错 |
| 趋势历史保存失败 | 返回分析结果，但提示历史未保存 |

这体现了两个重要原则：

1. **检索增强是可降级能力**：RAG 故障不一定要让主功能完全不可用。
2. **有副作用的操作必须幂等**：模型调用可以重试，但不能重复扣费。

---

# 五、这个实现的优点

面试可以重点讲：

- 多来源知识统一写入 VectorStore；
- 内容哈希避免重复索引；
- 多查询提高不同画像维度的召回覆盖；
- 类 RRF 的融合排序；
- 来源加权实现个性化偏好；
- RAG 故障可降级；
- 结构化数据库候选与向量知识结合；
- 幂等扣费和结果回放；
- 多 Key 并发槽位和供应商兜底；
- 对模型引用进行白名单清洗；
- 结果保存失败自动退款。

---

# 六、需要主动承认的不足

面试被问“你觉得还能怎么改”时，可以回答：

1. **缺少正式 RAG 评测**

   当前没有 Recall@K、MRR、答案忠实度和引用正确率，`topK=6`、阈值 `0.55` 主要是工程参数，不是基于评测集系统选出的。

2. **没有真正的重排序模型**

   当前只用向量召回排名、重复命中和业务来源加权，没有 Cross-Encoder Reranker。

3. **不是稀疏+稠密混合检索**

   没有 BM25，对精确仓库名、版本号、错误码等关键词查询可能不够好。

4. **JSON 解析较脆弱**

   当前主要使用正则解析，而不是标准 JSON Parser 或模型原生 Structured Output。遇到转义引号、嵌套对象时可能失败。

5. **Prompt Injection 防护有限**

   用户输入和检索文档直接拼入 Prompt，没有明确的数据/指令隔离和不可信内容标记。

6. **缺少片段级引用链**

   Prompt 有“片段1、片段2”，但最终回答没有强制每个结论关联具体 `docId/chunkIndex`。

7. **并发幂等还不是执行级完全互斥**

   已完成请求可以结果回放，配额不会重复扣除；但两个完全并发的相同请求仍可能同时调用模型，因为目前没有针对 `requestId` 的 in-flight 分布式锁。

8. **存在代码层小问题**

   V2 System Prompt 有重复段落；前端请求目前没有提交 `customExp`；领域历史在幂等结果回放后仍可能再次插入一条记录。

回答不足时，不要否定项目，可以说：

> 当前优先解决了可用性、成本与结果持久化问题；如果继续迭代，我会先建立检索评测集，再根据指标决定是否引入混合检索和 Reranker。

---

# 七、高频面试问题与回答要点

## 1. 什么是 RAG？

> RAG 是先从外部知识库检索与问题相关的内容，再把检索结果作为上下文交给大模型生成答案。它把知识存储和语言生成分开，适合需要实时更新、私有数据或可追溯依据的场景。

在 OrangeVC：

- Retrieval：`TrendContextBuilder.rag()`
- Augmentation：把前 4 个片段拼入 User Prompt
- Generation：`TrendModelClient.complete()`

## 2. 为什么不直接把所有知识放进 Prompt？

- 超过上下文窗口；
- Token 成本高；
- 无关信息会干扰模型；
- 每次请求都发送全量知识很慢；
- 难以根据问题动态选择证据。

## 3. RAG 和微调有什么区别？

> RAG 主要解决知识注入和知识更新问题，微调主要改变模型的行为模式、输出风格或特定任务能力。动态业务数据更适合 RAG；稳定行为模式可能适合微调。两者可以组合。

## 4. TopK 越大越好吗？

不是。

TopK 太小：

- 可能漏掉正确证据。

TopK 太大：

- 引入无关内容；
- 增加 Token；
- 造成上下文冲突；
- 降低模型对关键证据的注意力。

OrangeVC 每路先取 6 个，再融合为最终 4 个，是在召回覆盖和上下文长度之间折中。

## 5. 为什么需要 similarityThreshold？

TopK 无论结果多差都可能返回 K 条。阈值用于过滤语义上明显不相关的结果。

但阈值过高会漏召回，过低会引入噪声，所以应通过评测集选择，而不是凭感觉固定。

## 6. 为什么使用多查询？

> 用户画像包含方向、目的、经验和时间等不同语义维度。拆成多条查询可以防止所有字段拼接后语义被强关键词主导，并通过重复命中提高稳定相关文档的排名。

## 7. 如何减少幻觉？

当前已有：

- 提供真实图谱数据；
- 提供 RAG 片段；
- 提供数据库结构化候选；
- 对候选 URL 做白名单过滤；
- 对输出结构做完整性校验。

还可以改进：

- 强制引用 `docId/chunkIndex`；
- 没有证据时允许回答“不确定”；
- 增加 Reranker；
- 使用结构化输出；
- 做 groundedness 评测；
- 对关键事实使用程序校验。

## 8. 为什么选择 Redis VectorStore？

> 项目已经使用 Redis，并需要服务化、持久化和多实例访问，因此 Redis Stack 比纯本地向量库更符合现有部署架构。Spring AI 的 VectorStore 抽象也降低了未来更换 Milvus、PGVector 等实现的成本。

不要声称项目显式使用了余弦距离，因为当前业务配置没有明确写出距离度量；可以说具体相似度实现由 Redis VectorStore 的索引配置决定。

## 9. 这个 RAG 是 Agent 吗？

> 不是。它是确定性的 RAG 工作流。查询构造、检索次数、排序方式和模型调用顺序都由代码固定，LLM 不会自主决定是否检索或选择工具。它可以成为 Agent 的一个工具，但本身不等于 Agent。

---

# 八、面试中的完整回答模板

OrangeVC 中我实现的是一套手动编排的多源 RAG 流程。知识侧支持人工文档、GitHub README、岗位、新手项目和职业项目等来源。文档进入系统后先进行清洗和重叠分块，再通过 bge-small-zh-v1.5 生成 512 维向量，最终写入 Spring AI 抽象下的 Redis VectorStore；MySQL 负责保存原文、处理状态、内容哈希和文档元数据。

在线查询时，我没有把用户画像简单拼成一条长查询，而是按技术方向、学习目的、编程经验和时间投入拆成四路语义查询。每路从向量库召回 Top 6，并设置相似度阈值；随后使用 docId 和 chunkIndex 去重，通过类似 Reciprocal Rank Fusion 的方式累计多路排名分数，再根据新手项目、职业项目以及用户求职目的做来源加权，最终选取前四个片段。

生成阶段会把用户画像、GitHub 结构化图谱、RAG 片段以及数据库筛选出的项目候选共同组装进 Prompt，要求模型返回严格 JSON。模型返回后，系统还会做字段完整性校验、项目引用白名单过滤和结果持久化。

工程层面，请求带有 Idempotency-Key。后端同时校验请求 ID 和请求内容哈希，避免网络重试造成重复扣费；AI 调用层还实现了多 Key 并发槽位、429 和超时重试、无效 Key 隔离、模型兜底以及结果回放。VectorStore 不可用或单路检索失败时，系统会降级为非 RAG 分析，而不是让整个功能不可用。

当前不足是缺少正式的 Recall@K、MRR 和答案忠实度评测，也没有引入 BM25 或 Reranker；模型 JSON 目前主要通过正则解析，鲁棒性也有提升空间。如果继续迭代，我会先建立带正确文档标注的查询评测集，再根据评测结果调整分块、TopK、阈值和排序策略。

真正掌握的标准是：你能不看代码画出“入库链路”和“在线链路”，并能解释每个参数的作用、失败后发生什么、当前实现为什么这样设计、下一步如何通过指标改进。做到这些，RAG 相关的实习面试已经有足够深度。