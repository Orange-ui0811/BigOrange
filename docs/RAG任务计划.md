# RAG 趋势分析增强 — 可执行任务计划

> 基于「趋势分析 RAG 集成方案」和 tjxt-javaai02 参考架构，按自底向上顺序生成。
> 原则：先确认再实现 → 每一步有产出物 → 优先低风险可快速验证 → 最小闭环。

---

## 0. 当前基线

### 已有资产

| 资产 | 说明 |
|------|------|
| Spring Boot 3.3.5 + Java 17（POM）/ JDK 21.0.7（实际安装） | POM 需同步升级到 21（Step 1 首步执行） |
| Redis Stack（Docker） | RediSearch 模块已加载，向量索引已就绪 |
| Gitee AI embedding API | `bge-small-zh-v1.5`，512 维，中文效果验证通过 |
| Gitee AI chat API | `DeepSeek-V4-Pro`，已有 endpoint/key/model 配置 |
| 趋势分析 V1 + V2 | `TrendAnalysisService` + `AppTrendController` 已完整运行 |
| 榜单刷新定时任务 | `LeaderboardRefreshTask` 每周一拉 GitHub 数据 |
| 知识图谱 | `KnowledgeGraphServiceImpl` 提供双周数据 |
| tjxt-javaai02 | 参考项目：Spring AI + RedisStackVectorStore + QuestionAnswerAdvisor |
| `平台项目设计.md` | 完整业务模型 |

### 前置 P0/P1 验证结果

| # | 验证项 | 结果 |
|---|--------|------|
| P0-1 | Redis Stack RediSearch 模块 | ✅ 通过 |
| P0-2 | Gitee AI embedding API | ✅ 通过 — `bge-small-zh-v1.5` 512 维 |
| P0-3 | Spring AI 版本 | ✅ 1.0.0（与 tjxt 一致） |
| P1-1 | bge-small-zh-v1.5 中文效果 | ✅ 相关 0.77~0.80 / 无关 0.38~0.45，0.6 阈值完美切割 |
| P1-2 | API 返回格式 | ✅ OpenAI 兼容 `{data:[{embedding:[...]}]}` |

---

## 1. 计划总览（7 个步骤，按依赖链排列）

```
Step 1: Spring AI 基础设施   ██░░░░░░░░░░  约 0.5 天
Step 2: 知识库数据层        ███░░░░░░░░░  约 0.5 天
Step 3: 文档切片+Embedding  ██████░░░░░░  约 1.0 天
Step 4: 文档管理 API        ████████░░░░  约 0.5 天
Step 5: 自动采集 README     ██████░░░░░░  约 0.5 天
Step 6: 趋势分析 RAG 增强   ██████░░░░░░  约 1.0 天
Step 7: 前端知识库管理页    ████████░░░░  约 0.5 天
────────────────────────────────────────
总计                         约 4.5 天
```

**每个 Step 的结构：目标 → 输入 → 产出物 → 验证方式**

### 架构层次对照

```
┌─────────────────────────────────────────┐
│  Step 7: 前端 (KnowledgeBase.vue)        │  用户界面
├─────────────────────────────────────────┤
│  Step 4: Controller                      │  REST API
│    POST /app/knowledge/document/upload   │
│    GET  /app/knowledge/document/list     │
│    DELETE /app/knowledge/document/{id}   │
├─────────────────────────────────────────┤
│  Step 3: Service                         │  业务逻辑
│    DocumentService (切片+向量化+CRUD)     │
│    EmbeddingService (封装 API 调用)       │
├─────────────────────────────────────────┤
│  Step 2: Data Layer                      │  数据持久
│    knowledge_document 表 + Entity/Mapper │
├─────────────────────────────────────────┤
│  Step 1: Config (Spring AI)              │  基础设施
│    AiConfig.java + AiProperties.java     │
│    EmbeddingModel + VectorStore Bean     │
└─────────────────────────────────────────┘

Step 5: LeaderboardRefreshTask 改动  ← 横切：定时任务层
Step 6: TrendAnalysisService 改动    ← 横切：Service 层
```

---

## Step 1: Spring AI 基础设施

> **目标**：Maven 依赖引入 + Spring AI 配置 + Bean 注入验证
> **原则**：不改现有代码逻辑，只加配置。Ollama/Redis Stack 不可用时应用仍可启动。

### 1.1 Maven 依赖

**输入**：父 POM 新增 `spring-ai.version=1.0.0`（与 tjxt 一致）；Spring AI BOM 放在父 POM 的 `<dependencyManagement>` 中统一管理版本

**产出物**：

父 POM `pom.xml`（`<dependencyManagement>` 中）：
```xml
<spring-ai.version>1.0.0</spring-ai.version>
<!-- Spring AI BOM -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bom</artifactId>
    <version>${spring-ai.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

`orange-server/pom.xml`（`<dependencies>` 中）：
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-redis-store</artifactId>
</dependency>
<!-- 不需要 spring-ai-ollama-embedding-model —— 改为自己实现 EmbeddingModel 接口 -->
```

> ⚠️ **注意**：artifact 名称以 Maven Central 为准。不需要 Ollama starter——改为自定义 `GiteeAiEmbeddingModel` 实现 `EmbeddingModel` 接口。

### 1.2 配置属性类 `AiProperties.java`

**输入**：tjxt 的 `AIProperties.java` 模式

**产出物**：`orange-server/.../config/AiProperties.java`

```java
@ConfigurationProperties(prefix = "orange.ai-embedding")
public class AiEmbeddingProperties {
    /** Gitee AI embedding endpoint */
    private String endpoint = "https://ai.gitee.com/v1";
    /** API Key */
    private String apiKey = "";
    /** Embedding 模型名 */
    private String model = "bge-small-zh-v1.5";

    // 手写 getter/setter（项目不使用 Lombok）
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
```

> **关键设计**：新增配置用 `prefix = "orange.ai-embedding"`（kebab-case），与现有的 `orange.ai-trend.*` 保持 YAML 风格一致。`TrendAnalysisService` 的 `orange.ai-trend.*` 配置暂不迁移——改动范围控制在新增功能，不做重构。

### 1.3 Spring AI 配置类 `AiConfig.java`

**输入**：tjxt 的 `SpringAIConfig.java`

**产出物**：`orange-server/.../config/AiConfig.java`

核心 Bean：

| Bean | 说明 | 条件 |
|------|------|------|
| `RestTemplate embeddingRestTemplate` | 调 Gitee AI embedding API（设置超时） | 无条件 |
| `EmbeddingModel` | 封装 Gitee AI / Ollama embedding 调用 | 无条件（有 API key 时用 GiteeAI，没有时回退） |
| `VectorStore` | RedisStackVectorStore | `@ConditionalOnProperty("orange.ai.vector-store")`或检测到 Redis Stack |

**设计决策（借鉴 tjxt）**：

- 不在 `AiConfig` 里直接写 `new OllamaEmbeddingModel()`——而是自定义一个 `EmbeddingModel` 实现类，封装 Gitee AI HTTP 调用，这样不需要 Ollama 依赖都能跑
- `VectorStore` 用 `@ConditionalOnBean(RedisStackVectorStore.class)` 条件注入——RediSearch 不可用时不影响启动
- 配置类加 `@EnableConfigurationProperties(AiProperties.class)`

### 1.4 配置更新 `application-dev.yml`

**产出物**：`application-dev.yml` 新增：

```yaml
orange:
  ai-embedding:
    endpoint: https://ai.gitee.com/v1
    api-key: ${AI_EMBEDDING_KEY:}
    model: bge-small-zh-v1.5
```

`application-prod.yml` 同理，通过环境变量 `AI_EMBEDDING_KEY` 注入密钥，不硬编码。

> **注意**：`application-dev.yml` 中 `api-key` 默认值留空，开发时通过环境变量或 IDE Run Configuration 注入。不做明文硬编码。

### Step 1 验证

- [ ] 父 POM `java.version` 升级到 21，`mvn compile` 通过
- [ ] Spring Boot 启动日志：`AiEmbeddingProperties` 加载的 endpoint/model 值正确
- [ ] `EmbeddingModel` Bean 注入成功
- [ ] `VectorStore` Bean 注入成功（Redis Stack 可用时）
- [ ] **Redis Stack 不可用时应用仍可启动**——关闭 Redis 容器，重启应用，不报 Bean 注入错误

---

## Step 2: 知识库数据层

> **目标**：建表 + Entity + Mapper，为文档管理提供数据基础

### 2.1 建表 `knowledge_document`

**输入**：设计文档中的文档管理需求

**产出物**：在 `schema-v1.sql` 末尾追加：

```sql
CREATE TABLE IF NOT EXISTS knowledge_document (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    title        VARCHAR(256) NOT NULL COMMENT '文档标题（文件名或仓库名）',
    source       VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源: MANUAL / GITHUB',
    source_ref   VARCHAR(512)          COMMENT '来源引用（GitHub full_name 或文件路径）',
    chunk_count  INT          NOT NULL DEFAULT 0 COMMENT '切片数',
    file_size    BIGINT                COMMENT '原始文件大小（字节）',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_source (source),
    UNIQUE KEY uk_source_ref (source_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';
```

> `source_ref` 做 unique key——GitHub 仓库不可重复入库

### 2.2 Entity + Mapper

**输入**：建表 DDL + 现有 Entity 模式（参考 `TrendHistory`——不继承 `BaseEntity`，自声明 `id` 和 `createTime`）

**产出物**：

```
orange-pojo/.../entity/KnowledgeDocument.java     # 不继承 BaseEntity，自声明 id/createTime
orange-server/.../mapper/KnowledgeDocumentMapper.java
orange-server/.../resources/mapper/KnowledgeDocumentMapper.xml
```

核心方法：
- `insert` — 插入文档记录
- `selectAll` — 全部文档列表（按 create_time 倒序）
- `selectBySourceRef` — 按 source_ref 查（幂等校验）
- `deleteById` — 删除
- `selectById` — 单条查询

### Step 2 验证

- [ ] MySQL 中 `SHOW CREATE TABLE knowledge_document` 确认表结构
- [ ] Mapper 单元测试：insert → selectById → deleteById 通过

---

## Step 3: 文档切片 + Embedding Service

> **目标**：文档上传后自动切片→向量化→存入 Redis Stack；支持删除时同步清向量

### 3.1 DocumentService 接口

**输入**：Step 2 的 Mapper + Step 1 的 EmbeddingModel / VectorStore

**产出物**：`orange-server/.../service/DocumentService.java`

```java
public interface DocumentService {
    /** 上传文档：切片 → 向量化 → 存 MySQL + Redis */
    KnowledgeDocument upload(MultipartFile file);

    /** 文档列表 */
    List<KnowledgeDocumentVO> listAll();

    /** 删除文档：删 MySQL + 删 Redis 向量 */
    void delete(Long id);
}
```

### 3.2 DocumentServiceImpl 实现

**产出物**：`orange-server/.../service/impl/DocumentServiceImpl.java`

核心逻辑：

```
upload(file):
  1. 读文件内容（UTF-8）
  2. 校验：文件大小 < 1MB，仅支持 .md/.txt
  3. 切片：500 字/段，重叠 50 字 → List<String> chunks
  4. 判断是否超过 100 切片上限
  5. 批量调 embedding API → List<float[]> vectors
  6. 构建 Spring AI Document 列表（text + metadata{chunkIndex, docTitle, source}）
  7. vectorStore.add(documents)  ← 自动建索引
  8. 写入 knowledge_document 表（title, source, chunk_count）
  9. 返回文档对象

delete(id):
  1. 查 knowledge_document 表拿到 docId
  2. vectorStore.delete("docId == " + docId)  ← Spring AI VectorStore 的过滤语法
  3. 删 knowledge_document 表记录
```

**切片方法**（独立工具方法）：

```java
List<String> splitChunks(String text, int maxChars, int overlap) {
    // 500 字/段，重叠 50 字
    // 不切断 Markdown # 标题行（标题单独成段或保留在段首）
}
```

### 3.3 GiteeAiEmbeddingModel 实现

**产出物**：`orange-server/.../ai/GiteeAiEmbeddingModel.java`

```java
public class GiteeAiEmbeddingModel implements EmbeddingModel {

    private final RestTemplate restTemplate;
    private final AiProperties properties;

    @Override
    public List<float[]> embed(List<String> texts) {
        // POST {endpoint}/embeddings
        // Body: { model, input: texts }
        // 返回: List<float[]>（每个 512 维）
        // 用 RestTemplate 发送，超时 30s
    }

    @Override
    public float[] embed(String text) {
        return embed(List.of(text)).get(0);
    }
}
```

> **借鉴 tjxt**：tjxt 用 DashScope 的 EmbeddingModel 是 Spring AI 自动配置的。OrangeJD 环境没有自动配置（没引入 DashScope starter），改为手动实现 `EmbeddingModel` 接口封装 Gitee AI HTTP 调用。VectorStore 仍用 Spring AI 自动配置的 `RedisStackVectorStore`。

### Step 3 验证

- [ ] 上传一篇 1200 字中文 Markdown → 预期 3 个切片
- [ ] `redis-cli FT.SEARCH idx knowledge_document` 看到 3 条向量记录
- [ ] 删除文档后 Redis 向量消失
- [ ] 空文件/超大文件/非文本格式 → 返回合理错误

---

## Step 4: 文档管理 Controller

> **目标**：提供 REST API，Postman 可调通

### 4.1 KnowledgeController

**输入**：Step 3 的 DocumentService

**产出物**：`orange-server/.../controller/app/KnowledgeController.java`

```java
@RestController
@RequestMapping("/app/knowledge/document")
public class KnowledgeController {

    @Autowired
    private DocumentService documentService;

    @PostMapping("/upload")
    public Result<KnowledgeDocumentVO> upload(@RequestParam("file") MultipartFile file) {
        // 调 documentService.upload(file)
        // 返回 { id, title, chunkCount, source, createTime }
    }

    @GetMapping("/list")
    public Result<List<KnowledgeDocumentVO>> list() {
        // 调 documentService.listAll()
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 调 documentService.delete(id)
    }
}
```

> 按项目约定，管理端操作放 `/app/*`（公共知识库，所有登录用户可用）。后续如需管理员专属操作（批量删除等），再放 `/admin/*`。

### 4.2 KnowledgeDocumentVO

**产出物**：`orange-pojo/.../vo/KnowledgeDocumentVO.java`

```java
public class KnowledgeDocumentVO {
    private Long id;
    private String title;
    private String source;       // MANUAL / GITHUB
    private String sourceRef;    // GitHub full_name 或 null
    private int chunkCount;
    private Long fileSize;
    private String createTime;
}
```

### Step 4 验证

- [ ] `POST /app/knowledge/document/upload` → 200 + 返回 `{id, title, chunkCount}`
- [ ] `GET /app/knowledge/document/list` → 200 + 列表
- [ ] `DELETE /app/knowledge/document/{id}` → 200
- [ ] 鉴权通过（需 JWT token）

---

## Step 5: 榜单刷新自动采集 README

> **目标**：`LeaderboardRefreshTask` 刷新后自动拉 Top 3 仓库 README → 切片 → 向量化
> **原则**：try-catch 隔离，README 拉取失败不影响榜单数据写入

### 5.1 改动点

**文件**：`LeaderboardRefreshTask.java`

**改动位置**：`refreshAndSave()` 方法末尾，榜单数据写入 `leaderboard_snapshot` 成功后

**插入逻辑**：

```java
// 榜单数据写入成功后
if (topicName != null) {
    try {
        fetchAndIndexReadme(topicName, repos);  // 拉 Top 3 README
    } catch (Exception e) {
        log.warn("[RAG] README 拉取失败 topic={}, err={}", topicName, e.getMessage());
        // 不抛异常，不阻断主流程
    }
}
```

### 5.2 fetchAndIndexReadme 方法

```java
private void fetchAndIndexReadme(String topicName, List<Map<String, Object>> repos) {
    // repos 已是按 stars 排序的 Top 20
    int imported = 0;
    for (int i = 0; i < Math.min(3, repos.size()); i++) {
        Map<String, Object> repo = repos.get(i);
        String fullName = (String) repo.get("full_name");  // "torque3D/Torque3D"

        // 幂等：已入库的跳过
        if (documentService.existsBySourceRef(fullName)) {
            continue;
        }

        // 构造 README URL
        String readmeUrl = "https://raw.githubusercontent.com/" + fullName + "/master/README.md";
        String content = fetchUrl(readmeUrl, 10000);  // 10s 超时
        if (content == null) {
            // 尝试 main 分支
            readmeUrl = "https://raw.githubusercontent.com/" + fullName + "/main/README.md";
            content = fetchUrl(readmeUrl, 10000);
        }
        if (content == null) continue;  // 跳过无 README 的仓库

        // 切片 + 向量化 + 存入
        documentService.importFromGithub(fullName, content);
        imported++;
    }
    if (imported > 0) {
        log.info("[RAG] topic={} 自动采集 README {} 篇", topicName, imported);
    }
}
```

### 5.3 DocumentService 新增方法

```java
/** 从 GitHub 导入 README（自动采集） */
void importFromGithub(String fullName, String content);
```

实现逻辑与 `upload` 相同，区别是 `source=GITHUB`、`sourceRef=fullName`、`title=fullName`。

### Step 5 验证

- [ ] 手动触发 `LeaderboardRefreshTask.refreshAll()`（或等周一凌晨）
- [ ] 日志出现 `[RAG] topic=xxx 自动采集 README N 篇`
- [ ] `GET /app/knowledge/document/list` 出现 `source=GITHUB` 的文档
- [ ] 再次触发刷新，同一 repo 不重复入库
- [ ] 模拟 README 拉取超时（断网），榜单 `leaderboard_snapshot` 仍正常写入

---

## Step 6: 趋势分析 RAG 检索增强

> **目标**：`analyzeV2` 组装 prompt 前，分维检索知识库 → 拼入 prompt

### 6.1 改动点

**文件**：`TrendAnalysisService.java`

**改动位置**：`analyzeV2()` 方法，在 `userPrompt` 组装处（当前第 248-249 行）

**改动前**：

```java
String userPrompt = profile.toString() + "\n\n" + dataSummary +
    "\n\n请基于用户画像和数据，严格按 JSON 格式输出推荐结果。";
```

**改动后**：

```java
// ---- RAG 检索增强 ----
String ragContext = "";
if (vectorStore != null) {
    try {
        ragContext = buildRagContext(profile, topics, purpose, experience, timeCommitment);
    } catch (Exception e) {
        log.warn("[RAG] 检索失败，跳过增强: {}", e.getMessage());
        // 检索失败不影响主流程
    }
}

String userPrompt = profile.toString() + "\n\n" + dataSummary +
    (ragContext.isEmpty() ? "" : "\n\n## 知识库参考\n" + ragContext) +
    "\n\n请基于用户画像、数据和知识库参考，严格按 JSON 格式输出推荐结果。";
```

### 6.2 分维检索方法 `buildRagContext`

```java
private String buildRagContext(StringBuilder profile, List<String> topics,
        String purpose, String experience, String timeCommitment) {

    // ① 构建 4 条维度 query
    List<String> queries = new ArrayList<>();

    // 方向 query（取用户选择的 topic + 自定义）
    String dirQuery = "技术方向：";
    if (topics != null && !topics.isEmpty())
        dirQuery += String.join(" ", topics) + " 开源项目入门实践";
    if (customTopics != null && !customTopics.isEmpty())
        dirQuery += " " + customTopics;
    queries.add(dirQuery);

    // 目的 query
    String purposeQuery = "技术学习目的：" + nvl(purpose);
    if (customPurpose != null && !customPurpose.isEmpty())
        purposeQuery += " " + customPurpose;
    purposeQuery += purposeToSearchContext(nvl(purpose));  // 求职→"面试 项目经验 GitHub作品集"
    queries.add(purposeQuery);

    // 基础 query
    String expQuery = "编程经验：" + nvl(experience) + " 学习路径入门";
    if (customExp != null && !customExp.isEmpty()) expQuery += " " + customExp;
    queries.add(expQuery);

    // 时间 query
    String timeQuery = "时间投入：" + nvl(timeCommitment) + " 学习计划 时间管理";
    if (customTime != null && !customTime.isEmpty()) timeQuery += " " + customTime;
    queries.add(timeQuery);

    // ② 每条 query 检索 Top 2
    List<Document> allHits = new ArrayList<>();
    for (String q : queries) {
        List<Document> hits = vectorStore.similaritySearch(
            SearchRequest.builder().query(q).topK(2).similarityThreshold(0.6).build()
        );
        allHits.addAll(hits);
    }
    log.info("[RAG] 分维检索: 4 queries → {} hits", allHits.size());

    // ③ 合并去重 + 按多样性取 Top 3
    List<Document> selected = deduplicateAndSelect(allHits, 3);
    log.info("[RAG] 去重后选定 {} 片段", selected.size());

    // ④ 格式化为 prompt 片段
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < selected.size(); i++) {
        Document doc = selected.get(i);
        sb.append("参考片段 ").append(i + 1).append("：");
        if (doc.getMetadata().containsKey("docTitle")) {
            sb.append("（来源：").append(doc.getMetadata().get("docTitle")).append("）");
        }
        sb.append("\n").append(doc.getText()).append("\n\n");
    }
    return sb.toString();
}
```

### 6.3 辅助方法

```java
/** 目的映射到搜索关键词 */
private String purposeToSearchContext(String purpose) {
    return switch (purpose) {
        case "求职" -> "面试技巧 项目经验 GitHub作品集 技术简历";
        case "学习" -> "学习路径 入门教程 从零开始 进阶指南";
        case "赚钱" -> "商业变现 开源项目商业化 接单 freelancing";
        case "探索" -> "技术趋势 新兴方向 兴趣探索 跨界学习";
        default -> "";
    };
}

/** 合并去重，按多样性取 topN */
private List<Document> deduplicateAndSelect(List<Document> hits, int topN) {
    // 去重（基于 text 的 hashCode）
    // 按 score 降序排列
    // 优先覆盖不同维度 → 贪心算法：每次取 score 最高且不重复的
}
```

### 6.4 VectorStore 注入

在 `TrendAnalysisService` 中新增：

```java
@Autowired(required = false)
private VectorStore vectorStore;  // Redis Stack 不可用时为 null
```

### Step 6 验证

- [ ] 知识库有文档时，analyzeV2 prompt 包含「## 知识库参考」段
- [ ] 日志打点确认：4 queries → N hits → 去重后 3 fragments
- [ ] 低于阈值 0.6 的片段不出现在 prompt 中
- [ ] 知识库为空时，prompt 不追加该段，分析正常返回
- [ ] Redis Stack 不可用时（vectorStore == null），analyzeV2 正常返回
- [ ] 对比 RAG 前后的分析结果——RAG 后推荐含方法论指导和案例参考

---

## Step 7: 前端知识库管理页

> **目标**：简单版——上传按钮 + 文档列表 + 单条删除

### 7.1 新增页面

**产出物**：`orange-web/src/views/KnowledgeBase.vue`

组件结构：

```
┌─────────────────────────────────────────┐
│  知识库管理                              │
│  ┌─────────────────────────────────────┐│
│  │ [+ 上传文档]                         ││  ← el-upload 组件
│  └─────────────────────────────────────┘│
│  ┌─────────────────────────────────────┐│
│  │ 文档列表                             ││
│  │ ┌─────────────────────────────────┐ ││
│  │ │ 📄 面试准备指南.md               │ ││
│  │ │   MANUAL · 3切片 · 2026-06-24   │ ││  ← el-card / el-table
│  │ │                          [删除] │ ││
│  │ ├─────────────────────────────────┤ ││
│  │ │ 📦 torque3D/Torque3D            │ ││
│  │ │   GITHUB · 12切片 · 2026-06-24  │ ││
│  │ │                          [删除] │ ││
│  │ └─────────────────────────────────┘ ││
│  └─────────────────────────────────────┘│
└─────────────────────────────────────────┘
```

### 7.2 API 调用

在 `orange-web/src/api/` 中新增 `knowledge.js`：

```js
import request from './request'

export const uploadDocument = (file) => {
    const fd = new FormData()
    fd.append('file', file)
    return request.post('/app/knowledge/document/upload', fd, {
        headers: { 'Content-Type': 'multipart/form-data' }
    })
}

export const listDocuments = () => request.get('/app/knowledge/document/list')

export const deleteDocument = (id) => request.delete(`/app/knowledge/document/${id}`)
```

### 7.3 路由

`orange-web/src/router/index.js` 新增：

```js
{ path: 'knowledge', name: 'KnowledgeBase',
  component: () => import('../views/KnowledgeBase.vue'),
  meta: { title: '知识库管理' } }
```

### Step 7 验证

- [ ] `/knowledge` 页面可访问，不白屏
- [ ] 上传按钮：选择 .md 文件 → 上传成功 → 列表自动刷新
- [ ] 删除按钮：点删除 → 确认 → 列表刷新 → 文档消失
- [ ] 趋势分析页面功能不受影响

---

## 3. 全局技术约定

### 3.1 包结构

```
com.orangejd.server
├── config/
│   ├── AiConfig.java              # Spring AI 配置（新增）
│   └── AiProperties.java          # AI 配置属性（新增）
├── ai/
│   └── GiteeAiEmbeddingModel.java # Embedding API 封装（新增）
├── service/
│   ├── TrendAnalysisService.java  # 趋势分析（改动）
│   ├── DocumentService.java       # 文档管理接口（新增）
│   └── impl/
│       ├── DocumentServiceImpl.java  # 文档管理实现（新增）
│       └── KnowledgeGraphServiceImpl.java  # 不改
├── controller/app/
│   ├── AppTrendController.java    # 不改
│   └── KnowledgeController.java   # 知识库 API（新增）
├── task/
│   └── LeaderboardRefreshTask.java  # 榜单刷新（改动）
└── mapper/
    └── KnowledgeDocumentMapper.java  # 知识库 Mapper（新增）
```

### 3.2 配置管理约定（借鉴 tjxt）

| 约定 | 说明 |
|------|------|
| `@ConfigurationProperties` 集中管理 | 不用散落 `@Value`。`AiProperties` 统一管理所有 AI 相关配置 |
| `@ConditionalOnProperty` 开关 | `VectorStore`、`EmbeddingModel` 用条件注入，不可用时不影响启动 |
| `required = false` 兜底 | 在 `TrendAnalysisService` 中注入 `VectorStore` 时用 `required=false` |
| 配置前缀 `orange.ai-*` | 新增 `orange.ai-embedding.*`，与现有 `orange.ai-trend.*` kebab-case 风格一致 |

### 3.3 错误处理约定

| 层级 | 处理策略 |
|------|---------|
| Embedding API 调用 | 重试 1 次，失败抛 `RuntimeException`，上层捕获降级 |
| 切片处理 | 文件过大/空文件 → 抛 `IllegalArgumentException`，Controller 返回 400 |
| 向量检索 | try-catch，失败后返回空结果（不阻断分析流程） |
| 榜单刷新 README 拉取 | try-catch，单 repo 失败跳过继续下一个 |
| Redis Stack 不可用 | `VectorStore` 为 null，所有检索降级到无 RAG 模式 |

### 3.4 日志约定

统一 `[RAG]` 前缀，方便追踪：

```
[RAG] 切片完成: doc=xxx.md, chunks=12
[RAG] embedding 完成: 12 chunks → 12 vectors, cost=2340ms
[RAG] 向量存储: 12 documents added
[RAG] 分维检索: 4 queries → 6 hits
[RAG] 去重后选定 3 片段: source=[面试指南.md, Unity教程.md, 学习法.md]
[RAG] prompt 已拼接, 知识库片段数=3, prompt=5234 chars
```

---

## 4. 可并行执行的步骤

| 并行组 | 内容 |
|--------|------|
| Step 1 + Step 2 | 基础设施配置 和 数据层建表可并行（互相无依赖） |
| Step 3 之后 | 无并行——Step 3 依赖 Step 2 的 Mapper，Step 4 依赖 Step 3 的 Service |

> 这个项目改动是线性的（依赖链长），并行空间小。核心策略是每步完成后立即验证，快速试错。

---

## 5. 回归保护检查清单（每步完成后执行）

| # | 回归项 | 覆盖 Step |
|---|--------|----------|
| R1 | `GET /app/trend/graph` 正常返回图谱 | Step 1~6 |
| R2 | `POST /app/trend/analyze`（V1）正常 | Step 1~6 |
| R3 | `POST /app/trend/analyze-v2` 正常返回 JSON | Step 1~6 |
| R4 | 历史记录查询/保存/删除正常 | Step 1~6 |
| R5 | `LeaderboardRefreshTask.refreshAll()` 执行成功 | Step 5 |
| R6 | Spring Boot 启动不报 Bean 注入错误 | Step 1, 每次 |
| R7 | 未装 Redis Stack 时应用正常启动 | Step 1 |

---

## 6. 完成证据清单

- [ ] `docs/RAG任务计划.md`（本文件）—— 方案被确认
- [ ] Postman collection：3 个知识库 API 全部 200
- [ ] 知识库管理页截图：文档列表有数据（手动上传 + 自动采集）
- [ ] 趋势分析日志：`[RAG]` 打点完整，分维检索→合并→注入 prompt
- [ ] 同一画像 RAG vs 非 RAG 分析结果对比——推荐中出现方法论和案例
- [ ] `git diff` 覆盖：新增 6 个文件，改动 3 个文件
- [ ] `mvn clean install -DskipTests` 通过

---

## 7. 风险注记

| 风险 | 状态 | 应对 |
|------|------|------|
| Spring AI 1.0.0 artifact 名称不一致 | 🟡 | 写 pom 时查 Maven Central 为准 |
| tjxt 的 `spring-ai-starter-vector-store-redis` 可能需要配 Redis 连接参数 | 🟡 | 本地 Redis Stack 用默认 localhost:6379，有差异再加 |
| Gitee AI embedding API key 权限 | 🟢 | 已验证通过 |
| 大文件切片数爆炸 | 🟡 | 限制文件 1MB + 单文档 100 切片 |
| 分维检索全部低于阈值 | 🟡 | 降级策略：取最高分的一条（即使低于 0.6） |

---

## 8. 下一步行动

**建议立即启动 Step 1：Spring AI 基础设施**

具体动作：
1. 查 Maven Central 确认 Spring AI 1.0.0 的准确 artifactId（参考 tjxt 的 pom.xml）
2. 在父 POM 加 BOM，在 `orange-server/pom.xml` 加依赖
3. 创建 `AiConfig.java` + `AiProperties.java`
4. 更新 `application-dev.yml` / `application-prod.yml`
5. 实现 `GiteeAiEmbeddingModel`（封装 Gitee AI embedding HTTP 调用）
6. 启动验证：Bean 注入成功 + Redis Stack 不可用时启动不报错
