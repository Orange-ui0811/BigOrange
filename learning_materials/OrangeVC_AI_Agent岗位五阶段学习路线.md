# OrangeVC × AI Agent 算法工程师：五阶段学习路线

快照日期：2026-09-04  
原则：现有 OrangeVC 产品代码只读；学习 Demo 单独存放。所有面试表述都区分“项目已实现”“额外实践”“尚未实现”。

## 总顺序

1. `AgentAction`：先理解一次受控业务动作怎样被授权、确认、入库、异步执行和恢复。
2. `AiToolRouter`：再理解真正的模型调用怎样做结果复用、全局限流、Key 调度、降级、重试、审计和释放。
3. Python 训练—验证—测试：建立算法实验的正确数据边界和评估思维。
4. LLM 与向量检索理论：把 OrangeVC 的 Prompt、Embedding、VectorStore 和 RAG 代码提升为可迁移的理论能力。
5. LangGraph Demo：最后把前四阶段的状态机、条件路由、人工确认和工具执行映射到标准 Agent 框架。

顺序理由：先吃透项目里的真实执行链，再补通用算法理论，最后学框架。这样面试回答会从“我会调用框架 API”升级为“我知道框架替我解决了什么，以及哪些生产治理仍需自己实现”。

---

## 第一阶段：AgentAction——业务动作执行治理

### 学习目标

能不看代码完整讲出：设备身份从哪里来、动作权限在哪里检查、为什么付费或有副作用的动作要先 PREPARED、重复确认为什么不会重复启动、取消与执行如何竞争、服务重启后如何处理遗留任务。

### 必读代码

- `orange-server/src/main/java/com/orangejd/server/interceptor/AgentDeviceInterceptor.java`
- `orange-server/src/main/java/com/orangejd/server/service/AgentDeviceAuthService.java`
- `orange-server/src/main/java/com/orangejd/server/controller/agent/AgentActionController.java`
- `orange-server/src/main/java/com/orangejd/server/service/AgentActionPreparationService.java`
- `orange-server/src/main/java/com/orangejd/server/service/AgentActionExecutionService.java`
- `orange-server/src/main/java/com/orangejd/server/service/AgentActionRepository.java`
- `orange-server/src/main/java/com/orangejd/server/mapper/AgentActionMapper.java`
- `orange-server/src/main/resources/db/migration/V13__add_agent_device_bridge.sql`
- `orange-server/src/test/java/com/orangejd/server/service/AgentActionServiceTest.java`

### 核心链路

```text
Bearer 设备令牌
  -> AgentDeviceInterceptor：认证，建立 accountId / credentialId / scopes
  -> POST /agent/actions/prepare
  -> 动作白名单 + scope + 参数/前置条件/价格检查
  -> 只读动作：直接 SUCCEEDED
  -> 付费或副作用动作：写入 PREPARED，5 分钟确认有效期
  -> POST /{taskId}/confirm
  -> 条件更新 PREPARED -> QUEUED
  -> 本地两线程执行器
  -> 条件更新 QUEUED -> RUNNING
  -> 调业务服务
  -> SUCCEEDED / FAILED
```

状态集合：`PREPARED`、`QUEUED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED`、`EXPIRED`。

### 必须吃透的设计点

1. `prepare` 与 `confirm` 分离是人机确认边界，不是普通的两步接口拆分。准备阶段把已校验参数序列化进任务，确认时按任务执行，避免确认前后参数被替换。
2. `markQueued` 和 `markRunning` 都带旧状态条件，是数据库级 compare-and-set。重复确认最多只有一次能把 `PREPARED` 改成 `QUEUED`。
3. `cancel` 只允许 `PREPARED/QUEUED`；若工作线程先改成 `RUNNING`，取消失败；若取消先成功，工作线程的 `markRunning` 返回 0 并退出。
4. `account_id + credential_id + task_id` 构成设备侧所有权校验，不能拿别的设备任务执行或查状态。
5. 启动时把遗留 `QUEUED/RUNNING` 标记为 `FAILED/SERVER_RESTARTED`，属于“明确失败并要求核对”，不是自动恢复。
6. 配额在 prepare 时只是估价和余额快照，并未预占；真正扣费及退款由下游业务服务负责。

### 已实现能力与边界

- 已实现：白名单、scope、五分钟确认、持久化任务、条件状态迁移、重复确认保护、取消竞争保护、重启清理、结果与语音摘要。
- 边界：线程池是单 JVM 的固定 2 线程执行器，队列默认无界；没有持久化 worker 重新认领、超时任务扫描或跨实例调度。
- 边界：业务副作用与任务 `SUCCEEDED` 标记不在一个原子事务内。进程可能在副作用完成后、状态落库前崩溃，因此不能宣称 exactly-once。
- 边界：异常被转换成统一失败提示，但执行服务自身缺少详细错误日志，排障可观测性有限。

### 面试安全表述

> OrangeVC 用 AgentAction 做设备动作治理。只读动作可直接完成，付费或有副作用的动作先持久化为 PREPARED，并在 5 分钟内由用户确认。确认和开跑都通过带旧状态条件的 SQL 更新，保证重复确认不会重复启动；任务执行再进入具体业务服务。它目前是持久化状态加本地线程池，不是分布式工作流引擎，重启后会把中断任务标记失败而不是自动续跑。

### 完成标准

- 手画状态图并解释每条合法边。
- 回答：连续点两次确认会怎样？取消和开跑同时发生会怎样？服务在副作用后崩溃会怎样？
- 能从 Controller 一路定位到具体业务服务调用。

---

## 第二阶段：AiToolRouter——模型调用执行治理

### 学习目标

能讲清一次 AI 调用为什么不应直接拿 API Key 请求模型，以及 OrangeVC 如何在业务逻辑外统一治理容量、Key 健康、降级、重试、审计、幂等结果和配额补偿。

### 必读代码

- `orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolRouter.java`
- `orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolContext.java`
- `orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolPoolProperties.java`
- `orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolSlotLeaseService.java`
- `orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolRetryPolicy.java`
- `orange-server/src/main/java/com/orangejd/server/service/AiResultRecordService.java`
- `orange-server/src/main/java/com/orangejd/server/service/TrendAnalysisService.java`
- `orange-server/src/test/java/com/orangejd/server/foundation/aitool/AiToolRouterTest.java`

### 核心链路

```text
业务服务构造 AiToolContext
  -> requestId + accountId + requestHash 查已保存结果
  -> 命中：直接 replay，不占并发、不调模型
  -> 未命中：获取 Redis 全局并发租约
  -> 轮询获取可用 Gitee Key 槽位
       -> 有槽位：Gitee
       -> 槽位全忙/禁用/失效/限流：DeepSeek fallback
  -> 先写调用审计日志
  -> 每 30 秒续租
  -> 执行业务传入的 Operation(config)
  -> 必须保存结果
  -> 记录 SUCCEEDED 与指标
  -> finally 取消续租并释放全局/槽位租约
```

失败时：识别 HTTP 状态、超时和业务异常；401/403 将槽位标为 `INVALID`，429 按 `Retry-After` 标为 `THROTTLED`；可重试失败以指数退避再次执行，每一次尝试重新申请并释放租约。

### 必须吃透的设计点

1. 全局租约限制总 AI 并发；槽位租约保证同一个 Key 同时只被一个调用占用。这是两个不同维度的容量控制。
2. Redis Lua 把“清过期租约—数容量—写租约”和“检查槽位状态—占槽位”做成原子操作，适用于多实例竞争。
3. 租约值带唯一 leaseId，续租与释放时都校验值，防止旧请求误删已被新请求占用的锁。
4. `AiToolContext` 是治理元数据：purpose、requestId、accountId、businessType、businessId、legacyConfigType、requestHash。路由器不理解业务 Prompt，只管理调用生命周期。
5. 结果持久化属于成功条件：模型返回了但结果没保存，不算成功；保存失败会按业务类型尝试退配额。
6. 调用日志和业务结果不是一回事：前者回答“哪个 provider/Key/attempt 发生了什么”，后者用于用户查看和请求重放。

### 已实现能力与边界

- 已实现：已完成结果 replay、Redis 全局并发、Key 独占、轮转、禁用/失效/限流状态、fallback、租约续期释放、重试退避、调用审计、Micrometer 指标、结果持久化失败退款。
- 边界：`auto-retry-max-attempts=0` 在代码和当前 dev/prod 默认配置中代表无限重试。永久处于“被判为可重试”的失败可能让请求长期不返回，应在生产设置有限次数、总时间预算或转异步任务。
- 边界：replay 只复用已经保存的结果，没有 request 级 in-flight single-flight 锁；两个同 requestId 的并发首请求仍可能同时调模型。
- 边界：分析器若把某些故障作为 `{error: ...}` 正常返回而非抛异常，路由器会把它保存并记作调用成功。例如趋势图谱构建失败/扣费失败目前存在这种路径。
- 边界：租约能限制和回收容量，但不能让外部调用获得 exactly-once 语义。

### AgentAction 与 AiToolRouter 的关系

```text
AgentAction：用户是否授权这个业务动作？任务处于什么业务状态？
    -> TrendAnalysisService 等业务服务
        -> AiToolRouter：这次模型调用占哪个容量和 Key？失败如何重试？结果怎样保存？
```

当 Router 内部自动重试时，外层 AgentAction 通常仍是 `RUNNING`。Router 最终成功后外层才标 `SUCCEEDED`；最终抛错则外层标 `FAILED`。

### 面试安全表述

> 我把 AgentAction 和 AiToolRouter 看成两层治理：前者是用户授权和业务任务状态机，后者是模型资源调度。AiToolRouter 会先按请求身份复用已落库结果，再用 Redis 租约控制全局并发和单 Key 独占；Key 不可用时走 fallback，对 401、429、超时等分类治理，并保证 finally 释放租约。当前结果 replay 不是并发 single-flight，默认无限重试也需要生产化收口，这是我阅读代码后识别出的边界。

### 完成标准

- 能解释 global lease 和 slot lease 为什么必须同时存在。
- 能回答：401、429、所有 Gitee Key 忙、Redis 租约过期、结果保存失败分别会怎样。
- 能明确区分幂等结果复用、互斥锁、exactly-once 三个概念。

---

## 第三阶段：Python 训练—验证—测试

### 学习目标

建立严格的数据边界：训练集只拟合参数；验证集只做超参数、模型和阈值选择；测试集只在方案冻结后做最终泛化评估。

### 项目入口

- `scheme22_publish/7_19test_ml_residual_optuna.py`
- `scheme22_publish/evaluate_49_generalization.py`

### OrangeVC 当前流程

1. 每份 CSV 按时间排序，做角度校正、递归圆周中值滤波和特征工程。
2. 输入特征包含 sin/cos 圆周编码、速率、过去窗口滚动统计和 lag 特征；标签是“真值角度 - 滤波角度”的圆周残差。
3. 方位角 AZ 与俯仰角 EL 分别训练残差回归器。
4. 每个 Optuna trial 只在 train 上 `fit`，在 val 上预测；预测残差先 clip，再加回滤波角度。
5. 优化目标是 `P95 + 0.2 × MAE`，既关注尾部误差，也兼顾平均误差。
6. 选出最佳超参数后，代码目前仍只在 train 上重新拟合最佳模型。
7. test 阶段加载已保存模型，比较 corrected、filtered、ML final 的 MAE、RMSE、P90、P95、MAX，并输出 CSV 和图。

### 为什么不能混用

- 若用 test 选模型、调 clip 或改特征，test 就已经变成验证集，报告不再是无偏泛化估计。
- 同一采集序列相邻样本高度相关，随机打散会让几乎相同的时刻同时落入 train 和 test，指标虚高。
- 更可信的拆分单位应是日期、采集场景、设备、基站或完整轨迹，而不仅是行。

### 对当前脚本的准确评价

- 优点：支持显式 train/val/test 目录；单 CSV 也按时间 60%/20%/20% 切分；滚动与 lag 特征只使用过去值；test 逻辑与 Optuna 搜索分离。
- 风险：单 CSV 模式在切分前对整段数据做特征工程，其中 `dt` 的 99.5 分位截断使用全序列统计，存在轻微未来分布信息泄漏；应只用 train 统计量并冻结到 val/test。
- 风险：在同一验证集做很多次 Optuna trial 会逐渐对验证集过拟合。数据允许时应做 GroupKFold、按日期滚动验证或留一场景验证。
- 取舍：最佳参数确定后可用 train+val 重训以利用更多数据，但前提是所有决策已冻结且 test 完全未看；当前只重训 train 更保守，但少用了验证数据。
- 泛化：`evaluate_49_generalization.py` 在多个 case 和 anchor 上比较完整定位误差，比只看角度残差更接近真实使用效果。

### 面试安全表述

> 这个 Python 管线把模型训练、Optuna 验证选择和独立测试拆开。模型学习的是滤波后角度到真实角度之间的残差，验证目标采用 P95 加权 MAE，测试再比较校正、滤波、ML 三个阶段。由于数据是时间序列，我不会随机按行打散；更严格的做法是按日期、场景或基站做 group split，并保证所有预处理统计量只在训练集拟合。

### 完成标准

- 能解释参数、超参数、验证集过拟合、数据泄漏、分布漂移。
- 能手算 MAE/RMSE，并解释为什么 P95/MAX 对定位和工业异常更重要。
- 能设计一套按日期或场景的泛化实验，而不是只会调用 `train_test_split`。

---

## 第四阶段：LLM 与向量检索理论

### A. LLM 必会主线

1. Tokenization：文本先变成 token ID；token 不等于汉字或单词，费用与上下文长度按 token 计算。
2. Embedding：输入 token 变成向量表示；LLM 内部 token embedding 与 RAG 使用的句向量模型用途相关但不是同一个接口概念。
3. Transformer：自注意力用 Q、K、V 计算 token 间依赖，核心形式为 `softmax(QK^T / sqrt(d))V`；多头注意力学习不同关系。
4. 训练：预训练主要做下一个 token 预测；SFT 学指令格式；偏好对齐让回答更符合人类选择。RAG 不会修改模型参数。
5. 推理：temperature 控制分布平滑度，top-p 做核采样；低温不等于绝对正确，只是输出通常更稳定。
6. Prompt：system 规定角色与约束，user 提供任务和数据；结构化输出要有 schema、字段约束、解析失败处理和评估，不能只写“请输出 JSON”。
7. Tool calling：模型只负责提出结构化调用意图；鉴权、参数校验、权限、确认、幂等、超时、重试和审计必须由确定性代码完成。
8. 幻觉：LLM 优化的是条件概率，不是事实数据库。降低幻觉要靠检索证据、工具、约束输出、拒答策略和评估，不能靠一句 Prompt 根治。

### B. 向量检索必会主线

1. Embedding 把语义相近的文本映射到相近向量；入库文档和查询必须使用兼容的同一模型与版本。
2. 相似度常见三类：cosine 看方向、dot product 同时受方向和模长影响、L2 看欧氏距离；是否等价取决于是否归一化。
3. Exact search 全量比较，准确但随数据规模线性变慢；ANN 用近似索引换吞吐与延迟，例如 HNSW、IVF，PQ 进一步压缩向量。
4. chunk 决定召回单元。过大则语义混杂、Prompt 浪费；过小则上下文割裂。overlap 能保留跨边界语义，但会制造重复召回。
5. `topK` 控制候选数量，threshold 控制最低相似度。K 太小损失 recall，太大引入噪声并占上下文。
6. metadata filter 先限制租户、文档类型、时间、权限等范围；向量相似不等于访问授权。
7. dense 适合语义近义，BM25/sparse 适合精确关键词、型号和错误码；工业知识库通常适合 hybrid search，再用 reranker 精排。
8. 检索评估看 Recall@K、MRR、nDCG；回答评估看正确性、faithfulness、引用支持度和拒答质量。只看“答案读起来不错”不算评估。

### C. 映射回 OrangeVC

- `GiteeAiEmbeddingModel` 调 OpenAI 兼容 `/embeddings` 端点。
- `AiConfig` 提供 Spring AI `EmbeddingModel`，VectorStore 可用时启用 RAG。
- `ProjectKnowledgeDocumentWriter` / `DocumentServiceImpl` 负责把项目知识写入向量库。
- `TrendContextBuilder` 用 `similaritySearch` 检索：简单问答使用 topK=2、threshold=0.5；画像 RAG 使用 topK=6、threshold=0.55。
- 检索文本、图谱摘要、用户画像被拼进 Prompt，再经 `TrendModelClient` 调用模型。
- 当前项目证明的是 Redis VectorStore/Spring AI 的 RAG 实践，不应在简历中写成 FAISS、ChromaDB 或 Milvus 实战。

### 面试安全表述

> 我在 OrangeVC 里实际读通了 RAG 的写入和查询链路：知识文档经 EmbeddingModel 向量化写入 Redis VectorStore，请求时按 query 做相似检索，把召回文本与图谱、画像共同组装进 Prompt，再调用 LLM。理论上我会从 chunk、距离度量、topK/threshold、metadata filter、hybrid retrieval、rerank 和 Recall@K/faithfulness 两层评估来分析效果。项目当前没有证明 FAISS/Milvus 实战，所以我只说理解其索引与使用差异。

### 完成标准

- 能白板讲出 Transformer、自注意力、temperature/top-p、RAG 与微调的差别。
- 能解释 cosine/dot/L2、HNSW/IVF、chunk/topK/threshold 的取舍。
- 能设计离线检索标注集，并分别评估“没召回”和“召回了但生成错了”。

---

## 第五阶段：LangGraph 独立 Demo

### Demo 位置

`learning_materials/langgraph_demo/demo.py`

当前验证版本：LangGraph 1.2.11。Demo 不需要 API Key，不调用真实打印机，只模拟 3D 打印排障。

### 图结构

```text
START -> perceive -> diagnose -> decide
                                | high risk -> human_review -> execute/reject -> END
                                | otherwise -----------------> execute        -> END
```

### 你必须理解的五个对象

1. `PrinterState`：节点之间共享的显式状态；`trace` 使用 reducer 追加，而不是覆盖。
2. node：普通 Python 函数，读取 state，返回局部 state update。
3. edge：固定执行顺序；conditional edge 根据当前状态选择下一节点。
4. `compile()`：`StateGraph` 只是构建器，编译后才可 `invoke/stream`。
5. checkpointer + `thread_id` + `interrupt/Command(resume=...)`：高风险动作暂停，保存状态，用同一线程标识恢复。

### 已跑通结果

- 喷嘴疑似堵塞：`perceive -> diagnose -> decide`，风险 high，图在 `human_review` 暂停；批准后恢复并进入模拟 execute。
- 翘边：诊断为热床附着不足，风险 medium，条件边跳过人工确认，直接进入模拟 execute。
- 运行退出码为 0。

### 如何运行

```powershell
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
.venv\Scripts\python.exe -X utf8 demo.py
```

### 为什么先不用真实 LLM

这个 Demo 先证明 LangGraph 的执行模型，不把 API Key、模型随机性和网络错误混进来。下一步再把 `diagnose` 或 `decide` 替换为真实 LLM+RAG 节点；权限校验、人工确认、工具执行和审计仍保留为确定性节点。

### 与 OrangeVC 的类比及边界

| LangGraph Demo | OrangeVC 对应概念 |
|---|---|
| State | `agent_action_task` + `AiToolContext` |
| node | preparation / business service / tool operation |
| conditional edge | 是否确认、是否执行、是否重试 |
| interrupt + resume | PREPARED + confirm |
| checkpointer | 数据库任务记录（概念相似，机制不等价） |

严禁表述：“OrangeVC 使用 LangGraph 构建了 Multi-Agent”。准确表述：“OrangeVC 自研了受控动作状态机和 AI 调用治理；我另外跑通了 LangGraph 状态图 Demo，并能把两者在状态、路由和人机确认层做比较。”

### 完成标准

- 能从空文件重新写出 State、node、edge、conditional edge、compile、invoke。
- 能解释为什么恢复必须复用 `thread_id`，为什么 interrupt 前的副作用必须幂等。
- 下一步自行替换一个真实 LLM 节点，并给工具节点加超时、有限重试和审计记录。

---

## 建议节奏（12 天）

- 第 1—2 天：AgentAction，画状态图，完成三个竞态问答。
- 第 3—4 天：AiToolRouter，画两级租约图，推演 401/429/容量满/保存失败。
- 第 5—7 天：Python 流程，复现一次 train/val/test，做一版 group split 设计。
- 第 8—10 天：LLM 与向量检索，制作概念卡片和 RAG 评估表。
- 第 11—12 天：从头重写 LangGraph Demo；第 12 天做一次 10 分钟项目讲解和追问演练。

## 最终验收：五分钟项目讲解框架

1. 先说业务：OrangeVC 让设备触发趋势分析、方案生成等动作。
2. 再说安全执行：AgentAction 用设备 scope、prepare/confirm 和任务状态机治理副作用。
3. 再说模型资源：AiToolRouter 用 Redis 租约、Key 健康、fallback、重试、日志和结果 replay 治理模型调用。
4. 再说智能能力：项目有 Prompt、Embedding 和 Redis VectorStore RAG；清楚召回与生成的独立评估。
5. 最后说算法与框架：Python 项目证明训练/验证/测试和泛化评估能力；额外 LangGraph Demo 证明能使用标准 Agent 编排框架。
6. 主动披露边界：OrangeVC 不是 LangGraph/Multi-Agent；当前本地任务执行器与默认无限重试仍有生产化改进空间。

## 验证记录

- 2026-09-04：LangGraph 1.2.11 Demo 实际运行成功，退出码 0。
- 2026-09-04：`AgentActionServiceTest` 11 项、`AiToolRouterTest` 11 项、`AiToolRetryPolicyTest` 2 项，共 24 项通过，0 failure，0 error。
