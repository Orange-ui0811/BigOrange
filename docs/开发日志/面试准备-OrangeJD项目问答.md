# OrangeJD 完整项目 — 面试题及答案

> 基于「面试官出题逻辑分析」六层框架，覆盖全部模块：架构、账号、交易、WebSocket、榜单、AI 分析、RAG、审核、部署。
> 技术栈：Spring Boot 3.3.5 + Java 21 + MyBatis + Redis + Spring AI 1.0.0 + Vue 3

---

## 1. 项目架构（5 题）

### 1.1 介绍一下项目整体架构

三层 Maven 模块 + 两个独立前端：

```
orange-common    → 通用工具（Result、BaseContext、JwtUtil、PageResult）
orange-pojo      → 数据模型（Entity、DTO、VO）
orange-server    → 后端服务（Controller、Service、Mapper、Task、Config）
orange-admin     → 管理后台（Vue 3 + Element Plus，独立 Vite 项目）
orange-web       → 用户端（Vue 3 + Element Plus，独立 Vite 项目）
```

关键设计：`orange-pojo` 独立为模块——前端如果换成 React 或小程序，只需引入这个模块的数据结构，不用重写后端。

### 1.2 为什么用 MyBatis 而不是 JPA？

1. **SQL 可控**：17 张表、订单状态机 10 个状态，复杂查询需要多表 JOIN + 聚合统计。MyBatis XML 写 SQL 比 JPA 的 JPQL 更直观。
2. **升级兼容**：Spring Boot 2.7 → 3.3.5 升级时，MyBatis 只需换一个 starter 版本，JPA 从 `javax.persistence` 迁移到 `jakarta.persistence` 改动更大。
3. **团队习惯**：PageHelper 分页插件与 MyBatis 配合成熟，不需要额外封装。

### 1.3 BaseContext ThreadLocal 设计有什么优缺点？

**优点**：Service 层任何地方都能通过 `BaseContext.getCurrentId()` 获取当前用户，不需要层层传参。

**缺点**：
- 异步场景需手动处理（`AiAuditService` 里显式调用 `BaseContext.setCurrentId(0L)` 标记为系统操作）
- 如果忘记在 `afterCompletion` 清理，会有内存泄漏风险

项目的拦截器 `JwtTokenAppInterceptor` 在 `preHandle` 设置、`afterCompletion` 清理，保证了请求生命周期内不会泄漏。

### 1.4 Result<T> 统一返回格式有什么优缺点？

返回格式：`{ code: 1/0, msg: null/"错误信息", data: {...} }`

**优点**：前端只需判断 `code`，不需要处理 400/401/403/500 等 HTTP 状态码。

**缺点**：失去了 HTTP 语义——Nginx 日志和监控系统无法根据状态码做统计分析。

项目折中：全局异常处理器 `GlobalExceptionHandler` 将未捕获异常转为 `Result.error()`，对外仍返回 HTTP 200。

### 1.5 Spring Boot 2.7 → 3.3.5 升级踩了哪些坑？

1. **javax → jakarta**：所有 `javax.servlet.*` 改为 `jakarta.servlet.*`，涉及拦截器、WebSocket、文件上传等 20+ 个文件
2. **SpringDoc 替换 SpringFox**：Swagger 的注解全部从 `io.swagger` 换成 `io.swagger.v3.oas.annotations`
3. **JJWT 0.9 → 0.12**：API 签名方法完全变了，`parser()` → `parserBuilder()`，`setSigningKey()` → `verifyWith()`
4. **MyBatis**：升级到 `mybatis-spring-boot-starter 3.0.3`，需要声明 Jakarta 兼容版本
5. **循环依赖**：Spring Boot 2.7 默认允许，3.x 默认禁止。在 `application.yml` 加了 `allow-circular-references: true` 临时解决

---

## 2. 账号与鉴权（4 题）

### 2.1 JWT 双通道设计为什么要分 admin 和 app 两套？

如果共用一套 JWT：
- admin 拿到自己的 token 就可以访问 `/app/*` 接口
- app 用户拿到 token 就可以访问 `/admin/*` 管理端

分开两套密钥 + 两套拦截器，token 互不通用。即使 app 用户的 token 泄露，攻击者也进不了管理后台。

**配置隔离**：
```yaml
orange.jwt:
  admin-secret-key: ${JWT_ADMIN_SECRET}
  admin-ttl: 7200000
  admin-token-name: Admin-Token
  app-secret-key: ${JWT_APP_SECRET}
  app-ttl: 7200000
  app-token-name: App-Token
```

### 2.2 退出登录怎么实现？JWT 无状态的，怎么让它失效？

JWT 确实无状态——服务端不存。退出登录时，把 token 加入 Redis 黑名单：

```java
// TokenBlacklistService
redisTemplate.opsForValue().set(
    "blacklist:token:" + token,
    "1",
    remainingTTL,  // 使用 JWT 剩余的过期时间
    TimeUnit.MILLISECONDS
);
```

拦截器在 `preHandle` 中先查黑名单，命中则直接 401。Redis 黑名单的 TTL = 原 token 的 TTL——token 过期后黑名单自动清理，不占内存。

### 2.3 Redis 不可用时黑名单怎么处理？

`StringRedisTemplate` 注入用了 `@Autowired(required = false)`。如果 Redis 不可用：
- 黑名单写入 → null check 跳过 → 不抛异常
- 黑名单检查 → null check 跳过 → token 正常通过

**代价**：Redis 不可用时，退出登录的 token 不会被拦截。但这是可用性和安全性的权衡——宁可暂时放宽退出登录的安全性，也不能让整个应用因为 Redis 宕机而无法启动。

### 2.4 注册时 account 和 user_profile 怎么保证一致性？

`AuthServiceImpl.register()` 加了 `@Transactional`——account 和 user_profile 的 INSERT 在同一个事务里：

```java
@Transactional
public LoginVO register(RegisterDTO dto) {
    // 1. 插入 account
    Account account = new Account();
    account.setUsername(dto.getUsername());
    account.setPassword(bCryptPasswordEncoder.encode(dto.getPassword()));
    accountMapper.insert(account);

    // 2. 插入 user_profile（使用 account 的自增 ID）
    UserProfile profile = new UserProfile();
    profile.setAccountId(account.getId());
    profile.setNickname(dto.getNickname());
    userProfileMapper.insert(profile);

    // 3. 生成 JWT 返回
    return buildLoginVO(account);
}
```

account 插入成功但 profile 失败 → 整个事务回滚 → 不会出现孤立的 account 记录。

---

## 3. 内容发布与审核（4 题）

### 3.1 作品的审核状态机是怎么设计的？

```
DRAFT → PENDING_REVIEW → APPROVED/REJECTED
  ↑                          ↓
  └──────── 编辑后重置 ───────┘
```

关键设计：
- **编辑退回**：已通过的作品被作者编辑后，自动退回到 DRAFT 状态——因为内容变了，需要重新审核
- **下架 = REJECTED + PRIVATE**：管理员下架作品时同时设两个状态，市场上不可见但作者本人能看到
- **AI + 人工双层**：AI 先自动审（DeepSeek V4 Pro 多模态），管理员可覆盖 AI 结果

### 3.2 AI 自动审核是怎么实现的？如何处理多模态内容？

`AiAuditService` 的核心流程：

```
① 提取内容：标题 + 描述 + 封面图 URL + 正文中的 <img> 标签（最多 5 张，去重）
② 调 DeepSeek V4 Pro（多模态）→ 输入文本 + 图片 URL
③ 模型返回 JSON：{"action":"APPROVED/REJECTED","reason":"..."}
④ 解析响应：严格 JSON 正则 → 降级为关键词匹配（"通过"/"拒绝"） → 降级为 SKIPPED
```

**降级策略**：图片审核失败 → 自动切为纯文本模式重试。日志打 `[图片降级]` 标记。

**飞书通知**：审核结果通过 Webhook 推送交互式卡片——通过是绿色、拒绝是红色。

### 3.3 作品和需求共用同一套审核逻辑吗？

是的。`AuditService` 的 `auditWork()` 和 `auditRequirement()` 都是同一套流程：
- 查目标实体 → 校验当前状态是 PENDING_REVIEW → 更新 audit_status → 写 AuditRecord → 发通知

区别只在 `target_type` 字段（"WORK" 或 "REQUIREMENT"）。

### 3.4 怎么防止同一内容重复提交审核？

`WorkServiceImpl.submitForReview()` 检查当前状态：
```java
if (!"DRAFT".equals(work.getAuditStatus()) && !"REJECTED".equals(work.getAuditStatus())) {
    throw new BaseException("当前状态不可提交审核");
}
```
只有 DRAFT 和 REJECTED 状态可以提交。已处于 PENDING_REVIEW 的重复提交会被拒绝。

---

## 4. 交易闭环（5 题）

### 4.1 订单状态机有哪些状态？为什么设计成这样？

10 个状态：

```
CREATED → WAITING_DEPOSIT → DEPOSIT_PAID → IN_PROGRESS
  → WAITING_FINAL_PAYMENT → FINAL_PAID → DELIVERED → COMPLETED
  任何阶段 → CANCELLED / DISPUTED
```

**为什么分两段付款（定金 + 尾款）：**
- 保护双方利益：买家付定金后卖家开始工作，卖家交付后买家付尾款
- 如果一次性付全款，卖家拿钱不干活的风险太高
- 借鉴了外包平台的成熟模式

**超时机制**：`OrderTimeoutTask` 每天凌晨检查——7 天未交付或未付尾款的自动取消。

### 4.2 两条下单路径——协商模式和直接购买有什么区别？

| | 协商模式（Quote → Order） | 直接购买（Work → Order） |
|------|------|------|
| 触发方式 | 聊天 → 报价 → 接受 → 生成订单 | 作品详情 → 直接下单 |
| 适用场景 | 定制需求、议价 | 明码标价的标准作品 |
| source_type | "QUOTE" | "WORK" |
| 价格来源 | 报价单协商价格 | 作品的 WorkSaleOption 定价 |

进入 Order 后两条路径使用同一个状态机——无论怎么来的，后续流转逻辑一致。

### 4.3 怎么防止自己给自己下单？

Service 层做硬校验：
```java
if (currentId.equals(order.getSellerId())) {
    throw new BaseException("不能购买自己的作品");
}
```

这个校验在 `OrderServiceImpl.create()` 里，两条下单路径都会经过。

### 4.4 报价单的状态流转是怎样的？

```
PENDING_CONFIRM → ACCEPTED（接收方接受）
                → REJECTED（接收方拒绝）
                → CANCELLED（发送方取消）
                → EXPIRED（7 天超时）
```

**权限控制**：只有报价的接收方可以接受/拒绝——校验 `receiverId == currentId()`。这条规则如果漏了，任何人都可以接受别人的报价单。

### 4.5 每个状态变更都通知双方是怎么实现的？

`OrderServiceImpl` 的每个状态变更方法最后都调用：
```java
notificationService.send(buyerId, "订单状态更新", "您的订单已进入" + status);
notificationService.send(sellerId, "订单状态更新", "您的订单已进入" + status);
```

`NotificationServiceImpl` 写入数据库后，调用 `WebSocketServer.sendToUser()` 实时推送到接收方。
如果接收方不在线，通知存在 MySQL 中，下次登录时可以拉到。

---

## 5. WebSocket 实时通信（3 题）

### 5.1 WebSocket 怎么做鉴权？为什么不能在 HTTP 拦截器里做？

WebSocket 的握手请求虽然是 HTTP Upgrade，但**不经过 Spring MVC 的拦截器**。浏览器的 WebSocket API 不支持自定义请求头（只能通过 URL 参数）。

项目方案：JWT token 作为 URL 查询参数传入：
```
ws://localhost:8080/ws/{userId}?token=eyJhbG...
```

`WebSocketServer.onOpen()` 中手动解析 token 并校验 `userId` 与 token 中的 `id` 是否一致。

### 5.2 ConcurrentHashMap 存 Session 为什么不用 Redis？

WebSocket Session 对象不能序列化——它是和 TCP 连接绑定的。Redis 只能存"谁在线"这个布尔状态，不能存 Session 对象本身。

项目用 `ConcurrentHashMap<String, Session>` 存在线用户——它线程安全、读写都很快。缺点是服务重启后所有连接断开，需要客户端自动重连。

### 5.3 Nginx 配置 WebSocket 有什么注意事项？

```nginx
location /ws/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;           # 必须 HTTP/1.1
    proxy_set_header Upgrade $http_upgrade;     # 协议升级
    proxy_set_header Connection "upgrade";      # 保持连接
    proxy_read_timeout 86400s;        # 长连接超时 24 小时
}
```

缺少任意一个都会导致 WebSocket 连上就断。

---

## 6. 榜单系统（3 题）

### 6.1 GitHub 榜单数据怎么获取和存储？

三步：

```
① LeaderboardRefreshTask（每周一凌晨 2:00）
   → 15 个 topic × 2 个周期（all + week）= 30 次 GitHub Search API 调用
   → 每次调用间隔 6 秒（避免 rate limit）
   → 取 Top 20 仓库存入 leaderboard_snapshot 表

② LeaderboardServiceImpl.getGithubRepos()
   → 读今天的快照 → 没有就降级读昨天的（加"数据可能延迟"标记）

③ Redis 本地缓存
   → leaderboard:local:github:{topic}:{period} → TTL 15 分钟
   → 用自定义 JSON 序列化（不用 Jackson，避免依赖）
```

### 6.2 为什么用快照表而不是实时查 GitHub API？

1. **GitHub API 有频率限制**：未认证 60 次/小时，已认证 5000 次/小时。30 次调用周期刷新刚好在限制内
2. **用户访问快**：读 MySQL 要比等 GitHub API 返回快得多
3. **数据可比性**：同一天的快照可以做本周 vs 上周对比，实时数据没法做历史对比

### 6.3 快照表为什么用 JSON 存而不是多列？

`data_json` 字段存的是 `{"total": N, "records": [{...20个仓库的完整JSON...}]}`。选择 JSON 列的原因：
- 榜单数据结构随 GitHub API 变化——如果新增字段，改 JSON 比 ALTER TABLE 灵活得多
- 榜单只是展示数据，不需要 WHERE/ORDER BY 仓库字段——索引需求为零
- 30 天后自动清理，存储量不大

---

## 7. AI 趋势分析（4 题）

### 7.1 V1 和 V2 的设计差异和演变逻辑是什么？

| | V1 | V2 |
|------|-----|-----|
| API 调用方 | 用户自备 API Key | 服务端统一配置 |
| 输入 | 自由文本问题 | 四维画像（方向/目的/基础/时间） |
| 输出 | Markdown 自由文本 | 结构化 JSON |
| RAG | 无 | 分维检索 + 知识库注入 |
| 适用 | MVP 快速验证 | 生产正式版 |

**演变逻辑**：V1 先用"用户自备 Key"模式快速上线验证需求——不花自己的 API 费用。验证可行后 V2 改为服务端统一调用 + 结构化输出 + RAG 增强，提升体验。

### 7.2 知识图谱的节点和边是怎么构建的？

`KnowledgeGraphServiceImpl.buildCompareGraph()`：
- **节点**：15 个技术方向（topic），每个节点计算拥挤度、难度、增长率、机会分数（0-100）
- **边**：两个 topic 共享 ≥2 种编程语言时建立连线，连线越粗 = 技能迁移性越强
- **Vibe Coding 占比**：扫描每个 topic 的 repo 名称 + 描述，命中 40+ 关键词（"cursor""claude code""copilot"等）的仓库占该 topic 的比例

### 7.3 机会分数 0-100 是怎么算的？

```
opportunityScore = heat(30%) + growth(25%) + blue(25%) + active(20%)

heat    = 热度分（按 totalStars 排名）
growth  = 增长分（本周 vs 上周的增长率）
blue    = 蓝海分（starsPerRepo 越低越好——竞争少）
active  = 活跃分（repo 数量）
```

这个公式的核心思想：**热度高 ≠ 值得投入**。热度高但拥挤的方向（如 AI Agents）机会分反而低于"冷门但增长快"的方向。

### 7.4 Plan Designer 的 5 阶段提示词是怎么设计的？

`AppPlanController.planGenerate()` 分 4 个阶段（+ 精减模式）：

```
Phase 1: 需求理解 → 用户目标、技术任务、核心模块、MVP 边界
Phase 2: 成功标准 → 可验证标准、回归问题、完成证据
Phase 3: 约束风险 → 技术约束表、风险矩阵、非目标列表
Phase 4: 任务计划 → 步骤拆分、文件清单、回滚策略

每个阶段把上一阶段的输出作为 context 传给 AI
```

关键设计：阶段间传递上下文（`prevContents`）——后续阶段基于前置结论输出，不是从零开始。

---

## 8. RAG 知识库（6 题）

### 8.1 Spring AI 的 QuestionAnswerAdvisor 是做什么的？你的项目为什么没用它？

`QuestionAnswerAdvisor` 是 Spring AI 内置的 RAG Advisor——自动从 VectorStore 检索相关文档，把结果注入到 system prompt 中发给大模型。它在 ChatClient 的 advisor 链中自动触发，适合问答式对话场景（用户问一句、AI 答一句）。

我的项目**没有用**它，因为场景不同：

| | 问答对话（适合 Advisor） | 趋势分析（不适合） |
|------|------|------|
| 交互模式 | 一问一答 | 表单 → 分析 → JSON |
| prompt 结构 | 简单拼接 | 三段式：画像 + GitHub 数据 + 知识库参考 |
| 检索时机 | 每次对话 | 分析提交时一次 |
| 结果插入位置 | 随意（system prompt 末尾） | 精确（"## 知识库参考"段） |

Advisor 自动注入会打乱 prompt 三段式结构。我选择手动调 `vectorStore.similaritySearch()` 拿到 Top 3 片段，精确拼到指定位置。

---

### 8.2 分维检索"4 queries → Top 3"相比单 query 有什么优势？

**单 query 的问题**：把用户的所有画像维度拼成一句话检索，embedding 模型中技术名词（"Unity""C#"）的信号强度远大于抽象概念（"求职""时间管理"），导致 Top 3 结果全被方向维度抢走，其他维度颗粒无收。

**分维检索方案**：

拆 4 条 query，每条对应一个画像维度：
- 方向："游戏开发 Unity 开源项目入门实践"
- 目的："技术求职 面试技巧 GitHub作品集"
- 基础："Python转C# 学习路径入门"
- 时间："每天2小时 碎片时间学习法"

每条 query 各自检索 Top 2 → 合并 8 个候选 → 去重 → 按多样性取 Top 3（优先覆盖不同维度）。

**效果**：4 次 Redis 检索（毫秒级），确保三个核心维度至少各有一个片段被注入 prompt。日志验证：`[RAG] 分维检索: 4 queries → 5 unique fragments → prompt 已拼接, 知识库片段数=3`。

---

### 8.3 `initialize-schema` 默认 false 导致 FT.INDEX 不创建——怎么排查的？

**现象**：文档上传成功、Redis 里能看到 JSON key、但 `similaritySearch()` 始终返回空。

**完整排查过程**：

```
① redis-cli FT._LIST → 返回空 → FT.INDEX 未创建
② 查启动日志 → vectorStore.add() 没报错，但相似搜索返回 0 条
③ 解编译 RedisVectorStore 源码 → 发现 afterPropertiesSet() 在
   initializeSchema=false 时直接 return，不创建索引
④ 查 CommonVectorStoreProperties → boolean 默认值是 false
⑤ application-dev.yml 加 spring.ai.vectorstore.redis.initialize-schema=true
   → 重启后 FT.INDEX 创建成功 → similaritySearch 开始返结果
```

**根因**：Spring AI 1.0.0 的设计缺陷——`initializeSchema` 默认为 false，意味着"不自动创建索引"，但文档中未明确说明。高版本已修复此默认值。

---

### 8.4 中文 query 匹配英文 README 相似度只有 0.5——怎么解决的？

`bge-small-zh-v1.5` 对中文 query ↻ 英文 README 的余弦相似度仅 **0.505**，低于检索阈值 0.6，意味着 34 篇英文 GitHub README 对中文用户完全不可检索。

**试了四个方案**：

| 方案 | CN↔EN 相似度 | 结论 |
|------|-------------|------|
| A: 当前 bge-small-zh | 0.505 | 低于阈值 ❌ |
| B: 换 bge-m3（多语言） | 0.480 | 更差——中英文映射到不同子空间 ❌ |
| C: 吞入时加中文标签 | 0.606 | 刚好过阈值，但提升有限 ✅ |
| D: 整篇翻译为中文 | 0.80+ | 最佳——中文↔中文匹配 ✅ |

**最终方案 D 的实现**：调 DeepSeek V4 Pro 翻译 README 全文 → 保留 Markdown 结构 → 代码块不翻译 → temperature=0.1（技术文档不需要创意） → 翻译失败降级用原文。

**关键教训**：不盲信模型名里的"多语言"，必须实测验证。bge-m3 号称多语言但跨语言 cosine 距离反而更大。

---

### 8.5 知识库为什么分"all 永久"和"week 30 天"两层？

两层对应不同的知识价值：

| | all（全时段 Top 20） | week（每周 Top 3） |
|------|------|------|
| 内容价值 | 经典项目，长期参考 | 新兴项目，时效性强 |
| 数量 | 15 topic × 20 = 300 篇 | 15 topic × 3 = 45 篇 |
| 生命周期 | **永久保留** | **30 天自动清理** |
| source_ref | `owner/repo` | `owner/repo\|week` |

同一仓库同时出现在 all 和 week → 两条独立记录（unique key 不同）。30 天后 week 过期删除，all 保留。永久层提供稳定的知识底座（成熟框架），周更层提供新鲜的趋势信号。

清理在 `LeaderboardRefreshTask.refreshAll()` 末尾执行——删 30 天前 `source='GITHUB' AND source_ref LIKE '%|week'` 的文档及对应向量。

---

### 8.6 Docker 没开时 VectorStore Bean 创建崩溃导致整个应用起不来——怎么修的？

**现象**：`RedisVectorStoreAutoConfiguration.afterPropertiesSet()` 中调用 `jedis.ftList()`，连接成功但 Redis 不是 Stack 版 → `JedisDataException: ERR unknown command 'FT._LIST'` → Bean 创建失败 → 整个 Spring Context 崩溃 → 应用无法启动。

**修复方案**（三层降级）：

1. **排除自动配置**：`application-dev.yml` 中排除 `RedisVectorStoreAutoConfiguration`，不让它创建 VectorStore
2. **手动创建 VectorStore**：在 `AiConfig` 中用 `@ConditionalOnBean(JedisConnectionFactory.class)` 条件创建——Jedis 能连上才尝试
3. **TrendAnalysisService 降级注入**：`@Autowired(required = false) private VectorStore vectorStore`——VectorStore 为 null 时走无 RAG 路径，不影响分析功能

**效果**：Docker 开不开，应用都能正常启动。没开时日志提示：`⚠ RedisStackVectorStore 未就绪 — 应用正常启动，但 RAG 功能不可用。请确认：(1) Docker Redis Stack 已启动 (2) RediSearch 模块已加载 (3) client-type=jedis`

---

## 9. 安全与部署（4 题）

### 9.1 文件上传怎么防止攻击？

`LocalFileUploadServiceImpl` 四层防护：
1. **MIME 白名单**：只允许 image/png、image/jpeg、image/gif、image/webp
2. **大小限制**：最大 10MB
3. **随机文件名**：UUID 前 8 位重命名，防止目录遍历攻击
4. **静态资源隔离**：通过 Nginx 单独映射 `/uploads/` 路径，不暴露真实文件系统路径

### 9.2 IP 限流怎么实现的？

`AppTrendController` 中的模式：
```java
String rateKey = "rate:trend:analyze:" + ip;
if (redisTemplate.opsForValue().get(rateKey) != null)
    return Result.error("请求过于频繁，请 10 秒后重试");
redisTemplate.opsForValue().set(rateKey, "1", 10, TimeUnit.SECONDS);
```

关键点：
- 优先取 `X-Forwarded-For` 头（Nginx 代理后的真实 IP）
- Redis key 带 TTL——10 秒后自动释放，不用定时清理
- `StringRedisTemplate` 是 `required = false`——Redis 不可用时限制功能降级但不过度影响用户

### 9.3 生产环境怎么配置敏感信息？

全部走环境变量，`application-prod.yml` 里不写明文：
```yaml
spring.datasource.password: ${MYSQL_PASSWORD}
orange.jwt.admin-secret-key: ${JWT_ADMIN_SECRET}
orange.ai-trend.api-key: ${AI_TREND_KEY}
```

`start.sh` 里通过 `export` 注入环境变量。

### 9.4 Nginx 配置有什么容易踩的坑？

1. **`try_files` 位置**：如果放在 `server` 层会劫持所有请求（包括 POST API），必须放在 `location /` 内
2. **Admin `location ^~ /admin`**：必须带 `^~` 前缀优先匹配，否则 Nginx 的正则规则会截获 `/admin/assets/` 的静态资源请求
3. **`proxy_pass` 末尾 `/`**：`proxy_pass http://127.0.0.1:8080/;` 的末尾 `/` 会剥离 `/api` 前缀——没有这个 `/` 就是路径拼接

---

## 10. 交易模式开关（1 题）

### 10.1 交易模式开关怎么实现前端和后端的联动？

**后端**：Redis key `system:trade-mode` 存储布尔值。`AppConfigController.getTradeMode()` 返回当前状态，`AdminConfigController.toggleTradeMode()` 切换。

**前端**：
- `tradeMode.js` 用 Vue 3 响应式 ref 存储状态
- `MainLayout` 的 `onMounted` 中调 API 获取初始值
- 管理端 Dashboard 右上角的 `el-switch` 绑定 `toggleTradeMode()`
- **立即生效**：订单相关的路由和菜单项通过 `v-if="tradeEnabled"` 控制显示/隐藏——Redis 值一变，所有用户刷新页面后立即生效

---

## 11. 综合设计题（3 题）

### 11.1 如果让你重新设计这个项目，你会改什么？

1. **订单状态机用枚举而不是字符串常量**：10 个状态全用 `String` 比较，容易拼写错误。应该用 Java enum + `switch` 表达式
2. **加 Spring Actuator**：目前没有 health/metrics 端点，服务器出了性能问题只能 SSH 上去看日志
3. **抽取公共分页逻辑**：每个 Mapper XML 都有手写的 LIMIT + offset，可以写个 MyBatis 插件统一处理
4. **前端加 Pinia 状态管理**：目前用简单 ref 共享状态，页面多了不好维护

### 11.2 订单量大的时候，OrderTimeoutTask 每天扫全表有什么问题？

当前 `SELECT * FROM order WHERE order_status IN ('DEPOSIT_PAID','DELIVERED')` 是全表扫描。10 万+ 订单时会很慢。

优化：索引 `(order_status, create_time)`，SQL 加时间过滤：
```sql
WHERE order_status IN (...) AND create_time < NOW() - INTERVAL 7 DAY
```

### 11.3 如果要把这个单体应用拆成微服务，你会怎么拆？

1. **账号服务**（auth）：独立部署，其他服务通过 JWT 鉴权，不依赖中心 session
2. **内容服务**（work + requirement + category）：核心业务
3. **交易服务**（order + quote + payment）：状态机复杂，独立部署方便扩展
4. **消息服务**（conversation + notification）：WebSocket 常驻内存，独立部署减少对其他服务的影响
5. **AI 服务**（trend + audit + plan + rag）：对 GPU/API 延迟敏感，可以独立做缓存和限流

通信方式：内部用 Feign + 服务发现，异步通知用消息队列。
