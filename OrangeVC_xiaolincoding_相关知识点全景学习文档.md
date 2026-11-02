# OrangeVC × 小林 coding 相关知识点全景学习文档

> 目标：把小林 coding 公开资料中与 OrangeVC 项目、Java 后端、RAG/Agent 应用开发和生产部署有关的知识点，整理成一张可以真正学懂、可以面试回答、可以回到代码验证的地图。
>
> 使用原则：本文是基于公开目录和公开文章的二次归纳，不复制原站全文。每个知识点都要回答三个问题：它是什么、为什么 OrangeVC 需要它、我能否从代码或实验中证明自己理解了它。

## 0. 先看结论：你不需要把整个网站全部学完

小林 coding 的后端面试汇总覆盖 Java 基础、集合、并发、JVM、Spring、MySQL、Redis、网络、操作系统、算法、消息队列、分布式、系统设计、Linux、Git、Docker 和测试开发；首页还链接了 Agent、RAG、LLM 工具调用、LangChain 等 AI 应用专题。原站目录中的规模大致为：Java 基础 83 题、集合 50 题、并发 68 题、JVM 40 题、Spring 60 题、MySQL 94 题、Redis 48 题、网络 76 题、消息队列 38 题、分布式 26 题、系统设计 14 题、Linux 21 题、Git 24 题、Docker 22 题。

对 OrangeVC 最重要的不是“把所有题背下来”，而是下面这条主线：

```text
Vue 页面
  -> Cloudflare / HTTPS / Nginx
  -> Spring Boot Controller
  -> 业务 Service / MyBatis
  -> MySQL（事实与状态） + Redis（缓存、限流、租约、向量检索）
  -> GitHub 采集 / 榜单 / 结构化候选
  -> RAG 检索与 Evidence Card
  -> AiToolRouter / 模型供应商
  -> WebSocket 任务进度与结果回放
```

学习优先级：

| 优先级 | 必须掌握 | 典型面试追问 |
|---|---|---|
| P0 | Spring Boot、MySQL、Redis、HTTP/HTTPS、并发与幂等、RAG、Agent 工具调用 | “你的项目为什么这样设计？并发冲突怎么办？” |
| P1 | JVM、Java 集合、线程池、缓存一致性、限流/分布式锁、Docker/Nginx、性能测试 | “如果流量上升一倍，你先看哪里？” |
| P2 | 消息队列、分布式事务、服务治理、操作系统、完整系统设计 | “如果拆成多服务，数据和任务如何流转？” |
| P3 | C++、Go、JVM 深层 GC、模型训练、LoRA/RL 等 | 了解概念即可，不能把未做过的内容说成 OrangeVC 已实现 |

## 1. OrangeVC 先建立一张“面试事实地图”

### 1.1 项目中真实存在的技术事实

| 领域 | OrangeVC 中的落点 | 面试时应强调 |
|---|---|---|
| 后端框架 | Spring Boot 3、模块化单体、Controller/Service/Mapper | 不是简单 Demo，而是按用户、内容、知识库、AI 任务、Agent 设备拆分领域 Service |
| ORM | MyBatis、Mapper、参数绑定、事务 | 业务 SQL 和状态迁移由后端显式控制，关键写操作需要事务与条件更新 |
| 关系数据库 | MySQL | 保存用户、项目、任务、榜单快照、知识库元数据、动作状态等事实数据 |
| 缓存/协调 | Redis Stack | 缓存、限流、任务租约、ZSET 调度、幂等键、向量索引 |
| 向量检索 | Redis Vector Store + HNSW | 文档向量和 metadata 写入 Redis，查询按相似度检索，再做项目去重、来源加权、Top-K 证据组装 |
| AI 接入 | 自定义 HTTP 模型客户端、Spring AI EmbeddingModel | Spring AI 负责 Embedding/Document/VectorStore 抽象，模型生成由项目的 HTTP 客户端与 AiToolRouter 治理 |
| 采集任务 | Spring Scheduler + SystemTask + GitHub REST API | 周期调度、租约、分项进度、重试、内容哈希、幂等增量更新 |
| Agent | AgentAction、AiToolRouter、prepare/confirm | 高风险操作二阶段确认、并发租约、槽位调度、重试、回放与结果幂等 |
| 前端/实时 | Vue 3、Pinia、Element Plus、WebSocket | SPA 调用后端 API；长任务通过 WebSocket 推送状态和进度 |
| 生产部署 | Linux、Nginx、Cloudflare、HTTPS、systemd/健康检查 | 反向代理、TLS、域名接入、进程拉起和故障恢复 |

### 1.2 不能混淆的边界

- Redis 向量检索不是“模型理解项目”；它只完成向量近邻搜索和 metadata 返回。语义解释、候选排序和最终文本生成仍由应用逻辑与模型完成。
- 结构化候选不是 RAG。结构化候选来自数据库项目池、Topic/语言/目的的确定性匹配和排序；RAG 是在候选确定之后补充 README 事实证据。
- AiToolRouter 不是 Agent 大脑。它是模型/工具调用的执行治理层，负责并发名额、账号槽位、租约、重试和结果回放；AgentAction 才描述具体动作及其状态。
- “支持上百名用户同时调用”是执行治理层按名额排队/接纳的容量口径，不等价于上百个模型请求同时在供应商侧运行。面试时要主动说明测量口径。

## 2. Java 基础：只学能解释 OrangeVC 代码的部分

来源：[Java 基础面试题](https://www.xiaolincoding.com/interview/java.html)。

### 2.1 P0 必会清单

1. **JDK、JRE、JVM**：JDK 是开发工具包，JRE 是运行环境，JVM 执行字节码。OrangeVC 的 Spring Boot JAR 最终由 JVM 运行。
2. **值传递**：Java 参数一律值传递；对象参数传递的是引用值的副本。解释 Service 修改对象字段与重新给参数赋值的区别。
3. **基本类型、包装类型、装箱拆箱**：数据库可空字段、MyBatis 映射、`Integer` 比较不能随意用 `==`。
4. **`equals` 与 `hashCode`**：项目中使用 Map/Set 做候选去重、metadata 处理时必须保持契约。
5. **String/StringBuilder**：Prompt、README 片段、Evidence Card 组装使用字符串；循环拼接应理解对象创建成本。
6. **接口、抽象类、多态**：`EmbeddingModel`、`VectorStore` 等抽象的价值是解耦供应商和实现。
7. **泛型**：让集合和工具接口在编译期约束类型，减少运行时类型错误。
8. **异常体系**：区分可恢复的网络/供应商错误、参数错误、业务状态冲突；不要用 `catch(Exception)` 把所有错误吞掉。
9. **反射与注解**：Spring 通过反射、注解和 Bean 容器完成依赖注入、MVC 映射和事务代理。
10. **Lambda、Stream、CompletableFuture**：理解它们对集合处理和异步任务的帮助；同时知道长任务不能无限创建异步线程。
11. **序列化**：任务结果、WebSocket 消息、Redis 值需要明确 JSON schema；版本演进时要考虑兼容。
12. **BigDecimal**：金额、积分等业务数值不使用二进制浮点直接计算。

### 2.2 面试表达模板

> “OrangeVC 中我没有把所有逻辑写在 Controller 里，而是用接口和 Service 把模型接入、检索、任务执行、数据访问分层。这样做不仅方便测试，也允许用自定义 EmbeddingModel 替换供应商实现。Java 的多态在这里不是抽象概念，而是用于隔离基础设施变更。”

### 2.3 暂时不作为重点

深层序列化协议、Java 21 全部新特性、冷门反射技巧、C++/Go 语法。除非岗位单独要求，否则先保证能读懂 OrangeVC 的 Java 代码并解释设计原因。

## 3. Java 集合与并发：对应候选排序、任务池和幂等

### 3.1 集合知识点

- `ArrayList`：连续数组，随机访问快，适合候选列表的顺序处理。
- `LinkedList`：节点链表，随机访问慢；不要因为“插入快”就默认使用。
- `HashMap`：哈希桶、扩容、冲突处理；适合按 `candidateId`、`docId` 建索引做去重。
- `TreeMap/TreeSet`：有序结构，适合需要自然排序的临时场景，但候选排序若有多个业务权重要显式写 Comparator。
- `HashSet`：去重；必须正确实现 `equals/hashCode`。
- `PriorityQueue`：Top-K 的常见工具；了解小根堆可以把全量排序优化为 O(n log k)。
- `ConcurrentHashMap`：并发缓存/本地状态的基础；理解分段/桶级并发、复合操作不能只靠两次单独调用。

### 3.2 并发知识点

1. **线程、进程、线程池**：请求线程负责快速入队或读取结果，模型长任务交给受控执行池。
2. **线程池参数**：核心线程数、最大线程数、队列、拒绝策略；不要把最大线程数随意设成很大。
3. **安全发布和可见性**：`volatile` 保证可见性但不保证复合操作原子性。
4. **锁与 CAS**：理解互斥、CAS、自旋、锁粒度和死锁条件。
5. **CompletableFuture**：理解异常链、超时、取消和线程池选择。
6. **幂等**：重复请求得到相同业务结果；与“接口不报错”不是一回事。
7. **竞态条件**：两个确认请求同时读取 PREPARED，再同时推进状态会导致重复执行；必须使用条件 SQL、唯一键或原子脚本。
8. **限流与背压**：请求超过执行能力时排队、拒绝或降级，而不是无限堆积。

### 3.3 OrangeVC 的具体落点

- `AgentAction`：`PREPARED -> QUEUED` 只能由满足状态条件的 SQL 成功推进；受影响行数为 1 才代表本次抢到执行权。
- `AiToolRouter`：Redis ZSET + Lua 实现全局并发信号量；租约带 TTL，释放时比较 token，防止旧任务误删新租约。
- 结果回放：已完成任务先查询持久化结果，重复请求不再次进入模型调用路径。
- 采集任务：SystemTask 租约防止多个实例重复执行同一批 GitHub 子任务。

## 4. JVM：理解到能解释生产问题即可

来源：[Java 虚拟机面试题](https://www.xiaolincoding.com/interview/jvm.html)（若页面路径发生变化，可从原站 Java 面试目录进入）。

必须掌握：

- 堆、栈、方法区/元空间、程序计数器分别存什么。
- 对象创建、可达性分析、垃圾回收的大致流程。
- 新生代/老年代、Minor GC/Full GC 的区别。
- 常见 GC 日志、堆内存和线程栈的排查思路。
- 类加载器、双亲委派和 Spring Boot 打包运行的关系。
- 为什么“大量长字符串、未关闭缓存、无限任务队列”会造成内存压力。

OrangeVC 关联：README/Prompt 大文本、任务上下文、批量 GitHub 数据和 WebSocket 连接都会占用内存；性能问题先看是否把大对象长期留在内存，再看数据库和外部模型延迟。不要在面试中声称做过 GC 调优，除非确实保留过 GC 日志和对比实验。

## 5. Spring / Spring Boot / MyBatis：OrangeVC 后端的核心必修课

来源：[Spring 面试题](https://www.xiaolincoding.com/interview/spring.html)。

### 5.1 Spring 核心

- **IoC**：对象创建和依赖关系交给容器；Controller 注入 Service，Service 注入 Mapper/基础设施。
- **DI**：构造器注入优先，依赖关系显式、便于测试。
- **AOP**：把事务、日志、权限、监控等横切逻辑从业务代码中抽出。
- **动态代理**：JDK 代理基于接口，CGLIB 基于子类；理解 `@Transactional` 为什么依赖代理。
- **Bean 生命周期**：实例化、属性注入、初始化、使用、销毁；理解扩展点有助于排查启动问题。
- **Bean 作用域**：单例 Bean 不等于线程安全；不要在单例 Service 字段保存请求级可变状态。
- **循环依赖**：知道三级缓存和代理对象的基本目的即可。
- **事务失效**：同类内部调用、非 public 方法、异常被吞、数据库引擎不支持事务等都会导致误判。

### 5.2 Spring Boot

- 自动配置：根据 classpath、条件注解和配置装配默认 Bean。
- Starter：用依赖集合简化接入，Spring AI Starter/Redis/MySQL 等都体现了这一点。
- 配置管理：区分环境配置、密钥、业务参数；密钥不能写入 Git。
- Filter、Interceptor、AOP：明确各自所在层次和适用场景。
- Actuator/健康检查：判断应用进程活着不等于依赖全部可用；健康检查应覆盖数据库、Redis 等关键依赖。
- 内嵌 Tomcat：Spring Boot JAR 可独立运行，配合 systemd 进行生产拉起。

### 5.3 Spring MVC 请求流程

```text
HTTP 请求
 -> Nginx/Cloudflare
 -> Tomcat 接收
 -> DispatcherServlet
 -> HandlerMapping 找 Controller
 -> 参数绑定/校验
 -> Controller
 -> Service
 -> Mapper/Redis/外部模型
 -> JSON 响应或 WebSocket 推送
```

要能解释：为什么 Controller 不直接写 SQL；异常如何统一转换；参数校验在哪里做；请求日志如何关联 trace/request id。

### 5.4 MyBatis

- `#{}` 是参数绑定，防止 SQL 注入；`${}` 是字符串拼接，只能在可信的 SQL 片段场景谨慎使用。
- Mapper 是接口抽象，XML/注解承载 SQL；Service 负责业务事务和状态机。
- 事务边界应覆盖“读取状态—条件更新—写入日志”等原子业务。
- `select` 后在 Java 中判断再 `update` 存在竞态；优先使用 `UPDATE ... WHERE status = ?` 并检查影响行数。

### 5.5 Spring AI 在 OrangeVC 的边界

Spring AI 提供 `EmbeddingModel`、`Document`、`VectorStore` 等统一抽象，项目为 Gitee AI embedding 实现自定义模型适配；但多路查询、去重、来源加权、Top-K、Evidence Card、Prompt 组装和最终 DeepSeek HTTP 调用是项目自行实现的策略层。面试中要把“框架抽象”与“自研业务策略”分开讲。

## 6. MySQL：事实数据、状态迁移和查询性能

来源：[MySQL 面试题](https://www.xiaolincoding.com/interview/mysql.html)。

### 6.1 SQL 与建模

- 表、行、列、主键、唯一键、普通索引、外键分别解决什么问题。
- 规范化与反规范化：业务事实表保持一致性，榜单/统计可按读性能做冗余。
- `JOIN`、`EXISTS`、`IN`、聚合、窗口函数的适用场景。
- `CHAR/VARCHAR/TEXT`、数字类型、时间类型和字符集。
- 防重复：唯一索引、业务键、`INSERT ... ON DUPLICATE KEY UPDATE` 或条件写入。

### 6.2 InnoDB 与索引

- InnoDB 支持事务、行锁、MVCC、崩溃恢复，适合作为 OrangeVC 业务主库。
- 聚簇索引叶子节点存整行数据；二级索引叶子节点保存索引列和主键，必要时回表。
- B+ 树适合磁盘页访问；联合索引遵守最左匹配原则，遇到范围条件后后续列利用会受限。
- 覆盖索引可以避免回表；低选择性字段单独建索引通常收益有限。
- 索引不是越多越好：写入、空间和优化器选择都会付出成本。

### 6.3 SQL 执行与优化

面试要能顺着讲：连接器/解析器/优化器/执行器/存储引擎；能用 `EXPLAIN` 看访问类型、可能索引、实际行数、回表和排序；能识别隐式类型转换、函数包列、前置模糊匹配、`OR`、不合理分页等导致索引失效的写法。

### 6.4 事务与并发

- ACID：原子性、一致性、隔离性、持久性。
- 隔离级别：读未提交、读已提交、可重复读、串行化；MySQL 默认可重复读。
- MVCC：隐藏列、undo log、Read View；解决普通读和写之间的并发问题。
- 当前读与快照读的差异；`SELECT ... FOR UPDATE` 的适用边界。
- redo log：崩溃恢复；binlog：归档、复制和恢复；两阶段提交保证二者协调。
- 间隙锁/临键锁：范围条件和高并发写入时可能导致锁等待。

### 6.5 OrangeVC 应用回答

> “用户、项目、任务、榜单快照和动作状态等需要事实一致性的数据放 MySQL；Redis 只做可失效的缓存、协调和向量索引。高风险动作确认不是先查状态再无条件更新，而是使用带旧状态条件的更新并检查影响行数，从数据库层保证只有一个并发请求成功推进状态。”

## 7. Redis：缓存、ZSET 调度、Lua、限流和向量数据库

来源：[Redis 面试题](https://www.xiaolincoding.com/interview/redis.html)。原站目录明确覆盖数据结构、线程模型、事务、持久化、缓存淘汰、集群、热点/大 Key、缓存一致性和高并发场景。

### 7.1 数据结构与 OrangeVC 映射

| Redis 能力 | OrangeVC 可能/实际用途 | 必须讲清 |
|---|---|---|
| String | 简单缓存、幂等标记、租约 token、任务结果摘要 | TTL、原子 SET NX EX、比较值释放 |
| Hash | 任务状态、对象缓存、配置 | 字段更新与整体过期的差异 |
| Set | 去重、标签集合、候选项目集合 | 无序唯一、交并差 |
| ZSet | 榜单、模型租约/等待队列、按分数排序 | score、范围查询、原子更新 |
| Stream | 若扩展异步事件流 | 消费组、消息 ID、重试与积压 |
| Lua | 并发信号量、租约续期/释放的组合原子操作 | 脚本在 Redis 内原子执行，不能调用外部网络 |
| HNSW Vector | README/项目证据向量相似检索 | ANN 近似近邻、metadata 过滤、召回与精度权衡 |

### 7.2 Redis 为什么快

内存访问、简单数据结构、单线程事件循环和 I/O 多路复用减少锁竞争；但“单线程”不表示所有工作都只有一个线程，持久化、网络读写等部分版本相关。真正瓶颈还可能来自网络、慢命令、大 Key、内存淘汰和序列化。

### 7.3 TTL、过期删除和淘汰

- 过期删除是“这个 key 到时间后如何清理”；内存淘汰是“内存满了以后淘汰哪些 key”。
- 惰性删除 + 定期删除意味着过期 key 不保证到点瞬间消失。
- 任务租约必须设置 TTL，否则进程宕机后会永久占用名额。
- 结果缓存应区分业务可接受的旧数据和必须实时的数据。

### 7.4 缓存三大问题

- 缓存穿透：请求不存在的数据；可用参数校验、空值缓存、布隆过滤器。
- 缓存击穿：热点 key 过期瞬间大量请求打到数据库；可用互斥重建、逻辑过期、预热。
- 缓存雪崩：大量 key 同时失效或缓存整体故障；可用随机 TTL、分批过期、降级和限流。

### 7.5 Redis 向量检索基本功

1. 文本通过 embedding 模型变成固定维度向量。
2. 向量和 metadata 写入 Vector Store。
3. 查询文本也转成向量。
4. HNSW 索引找近邻候选，返回相似度与 metadata。
5. 应用层做 docId/chunkIndex 去重、来源加权、项目白名单和 Top-K 证据组装。

要能回答为什么不用单独 FAISS/Milvus：当前数据规模和部署形态下，Redis Stack 已同时承担缓存、任务协调和向量检索，减少运维组件；代价是向量容量、隔离性和大规模 ANN 能力不如专用向量数据库。FAISS 更像本地索引库，Chroma 更适合轻量原型，Milvus/Qdrant 更适合独立的大规模向量服务。

## 8. HTTP、HTTPS、Nginx、Cloudflare、WebSocket

来源：[计算机网络面试题](https://www.xiaolincoding.com/interview/network.html)。

### 8.1 一次网页请求

```text
浏览器解析域名
 -> DNS 得到 Cloudflare 地址
 -> HTTPS/TLS 握手
 -> Cloudflare 边缘节点
 -> Nginx 反向代理
 -> Spring Boot/Tomcat
 -> MySQL/Redis/模型服务
 -> 响应经原路返回
```

### 8.2 概念必须分清

- **DNS**：把域名映射到 IP，不负责业务请求处理。
- **HTTP**：应用层请求协议，无状态；状态由 Cookie、Session、Token/JWT 或服务端存储补足。
- **HTTPS**：HTTP + TLS，提供加密、身份校验和完整性保护，默认 443。
- **Nginx**：七层反向代理/静态文件服务器/负载均衡/限流入口，不是数据库也不是应用业务层。
- **Cloudflare**：DNS、边缘代理、CDN、WAF、TLS 和流量防护平台；它可以隐藏源站、缓存静态内容，但不能替代后端鉴权和数据库安全。
- **WebSocket**：在 HTTP 握手后升级为长连接，适合服务器主动推送任务状态；与普通 HTTP 长轮询相比减少反复建连和请求开销。

### 8.3 高频追问

- TCP 三次握手为什么不是两次；四次挥手与 TIME_WAIT。
- HTTP 502 与 504：前者通常是上游无效响应/连接失败，后者通常是上游超时。
- Nginx 反向代理与正向代理区别。
- WebSocket 断线重连、心跳、消息顺序、重复消息和鉴权如何处理。
- Cloudflare 开启代理后源站如何限制只允许 Cloudflare 回源 IP。

## 9. 消息队列、分布式和系统设计：作为扩展答案

### 9.1 什么时候需要消息队列

当前 OrangeVC 可用任务表 + Redis 租约管理异步任务；当采集规模、通知、模型任务进一步增长，才考虑 MQ。消息队列的价值是解耦、削峰、异步和可重试，不是“用了就更快”。

必须掌握：生产者/消费者、消费组、ack、重复消费、至少一次/至多一次、消息顺序、积压、死信、重试、幂等和事务消息。

### 9.2 分布式常见问题

- CAP、BASE 与强一致的取舍。
- 分布式锁：唯一 token、TTL、续期、释放时比较 token；锁不是万能，业务必须有幂等兜底。
- 分布式 ID：自增、UUID、雪花算法的顺序性和时钟问题。
- 分布式事务：本地事务、Outbox、TCC、Saga；不要把跨 MySQL/Redis/模型调用的全过程假设成一个数据库事务。
- 服务治理：超时、重试、熔断、降级、隔离、限流和可观测性。

### 9.3 结合 OrangeVC 的系统设计题

题目：如何设计“GitHub 项目定时采集 + 榜单 + 个性化推荐”？

回答顺序：

1. 明确数据源、更新频率、规模、失败重试和一致性目标。
2. MySQL 设计项目、快照、采集任务、内容哈希和知识库元数据表。
3. Scheduler 生成任务，租约保证单实例执行；每个子项有状态和重试次数。
4. 采集完成后内容哈希不变则跳过 embedding；变化才重新切片、向量化并更新。
5. 推荐先确定性筛选候选，再由 RAG 补充证据，模型只能从候选白名单选项目。
6. 通过指标记录成功率、采集耗时、检索 P95、引用正确率、模型调用量和 Token。

## 10. Linux、Git、Docker 与部署

### 10.1 Linux

必须会：进程/端口查看、日志追踪、磁盘/内存/CPU 检查、权限、环境变量、systemd 服务状态、健康检查、滚动日志和基础网络诊断。面试不是背命令，而是能回答“线上接口变慢时怎么排查”。

推荐排查顺序：

```text
用户现象 -> Nginx 状态/日志 -> Spring Boot 日志与线程池
 -> MySQL 慢查询/连接池 -> Redis 慢命令/内存
 -> 外部模型耗时/错误 -> 机器 CPU、内存、磁盘和网络
```

### 10.2 Git

掌握工作区/暂存区/提交、分支、合并、rebase 的区别；能安全回滚、查看差异、定位引入问题的提交；密钥、`.env`、生成文件不应提交。面试时说明如何为一次实验建立独立分支并保留可复现结果。

### 10.3 Docker

来源：[Docker 面试题](https://www.xiaolincoding.com/interview/docker.html)。

- Namespace 提供进程/网络/挂载隔离，cgroups 限制 CPU/内存等资源。
- 镜像是只读模板，容器是运行实例，Registry 负责分发。
- 镜像分层、写时复制、构建缓存和多阶段构建。
- `CMD` 是默认命令，`ENTRYPOINT` 是主命令；exec 形式有利于信号转发。
- Volume/Bind Mount/tmpfs 的持久化差异。
- Docker Compose 适合单机组合启动 Spring Boot、MySQL、Redis；跨主机编排才考虑 Kubernetes。

OrangeVC 当前主要依赖 Linux + Nginx + systemd，是否使用 Docker 要以代码和实际部署证据为准，不能为了简历强行声称容器化。

## 11. 测试开发与性能：把“我做过”变成证据

### 11.1 单元、集成、隔离、压测

- 单元测试：Service/排序/切片/状态机等纯逻辑。
- 集成测试：MySQL、Redis、Spring 上下文和 Mapper。
- 隔离测试：验证并发状态迁移只成功一次、租约不越权、结果回放不重复调用。
- 压测：固定数据、固定并发、预热、重复次数、P50/P95/P99、错误率和资源利用率。

### 11.2 RAG 评测

- Hit@K：相关项目是否出现在前 K 个。
- Recall@K：真值相关项被召回的比例。
- MRR/NDCG：相关项在排序中靠前的程度。
- Citation accuracy：回答中的 URL/事实是否由检索证据支持。
- Unsupported claim rate：无依据关键描述比例。
- 延迟：向量检索、候选筛选、Prompt 组装和模型生成分开记录。
- 成本：输入/输出 Token、Embedding 调用次数、重复结果回放命中率。

### 11.3 结果写法边界

可以写：固定画像、真实索引、并发数、P95、成功率、引用正确率、Token 变化和实验方法。

不能写：没有真实压测就写“支持百万并发”；没有统计学设计就写“准确率提升 30%”；把检索耗时写成完整模型响应耗时；把排队接纳数写成供应商真实并发数。

## 12. Agent / RAG / LLM 工具调用：OrangeVC 的 AI 主线

AI 主题入口可从 [小林面试笔记](https://xiaolinnote.com/) 的 Agent、RAG、LLM 工具调用和 LangChain 专题进入；小林 coding 主页也将这些专题列为 AI Agent 面试篇。

### 12.1 RAG 全链路

```text
GitHub README / 项目资料
 -> 清洗、翻译、切片、metadata
 -> Embedding
 -> Redis HNSW VectorStore
 -> 查询改写/多路查询
 -> 向量召回 + 关键词召回
 -> docId/chunkIndex 去重
 -> 来源/画像/相似度加权
 -> 结构化候选白名单过滤
 -> Evidence Card 压缩
 -> Prompt
 -> 模型生成
 -> URL/事实引用校验
```

必须理解的理论：Embedding 空间、余弦相似度/距离、ANN 与 HNSW、chunk 粒度、overlap、metadata filter、召回率与精确率、重排序、query rewrite、上下文窗口、Lost in the Middle、幻觉和拒答。

### 12.2 为什么 OrangeVC 先结构化候选再用 RAG

结构化候选解决“推荐哪些已存在且可核验的项目”；RAG 解决“为什么推荐、项目具备哪些 README 可证实能力”。如果不做白名单，模型可能编造项目、URL 或技术栈；如果只做白名单而没有证据，模型可能对项目能力做无依据描述。

### 12.3 Agent 与工具调用

- LLM 负责理解意图和生成结构化 action；工具负责执行确定性操作。
- ReAct 是思考/行动循环；Plan-Execute 先规划再执行；Workflow 是固定流程；Agent 具有运行时决策能力。
- 工具 schema 需要描述名称、参数、返回值、错误和权限。
- 工具调用必须有超时、重试上限、幂等键、审计日志、权限边界和人工确认。
- 高风险动作采用 `prepare -> confirm -> queued/running -> succeeded/failed` 状态机。

### 12.4 AgentAction 与 AiToolRouter

```text
用户请求
 -> 生成/校验 AgentAction
 -> 低风险可直接执行，高风险进入 PREPARED
 -> 用户确认
 -> 条件 SQL 抢占状态
 -> AiToolRouter 获取全局并发名额和模型槽位
 -> 调用模型/工具
 -> 续租、超时、重试或失败
 -> 持久化结果
 -> 重复请求直接回放
```

面试时要说清：AgentAction 是业务动作和状态；AiToolRouter 是执行治理；Redis Lua 只能保证 Redis 内部组合操作原子，不替代 MySQL 事务；模型调用完成后必须有结果落库和幂等回放。

### 12.5 Prompt Engineering

掌握：角色/目标/约束/输入/输出 schema、few-shot、反例、引用要求、拒答条件、版本管理、离线评测和回归集。OrangeVC 的 Evidence Card 不是为了“看起来高级”，而是把长片段压缩为事实、来源、置信度和可引用字段，减少无关上下文并约束模型。

### 12.6 LangGraph 需要会到什么程度

你可以单独学习一个最小 Demo，但不要把 LangGraph 说成 OrangeVC 已经使用的运行时，除非代码确实接入。必须理解：State、Node、Edge、条件路由、循环、checkpoint、human-in-the-loop、工具节点、错误重试和可观测性。把 LangGraph Demo 当作迁移能力证明，而不是项目事实。

## 13. 最终面试回答模板：两分钟讲清 OrangeVC

> “OrangeVC 是我独立开发并上线的 AI 创作者和开发者项目平台。前端是 Vue 3 双 SPA，后端使用 Spring Boot 3 和 MyBatis 按用户、内容、知识库、AI 任务等领域拆分 Service，MySQL 保存业务事实，Redis Stack 同时承担缓存、限流、任务租约、ZSET 调度和向量检索，入口通过 Cloudflare、HTTPS 和 Nginx 反向代理进入应用。
>
> 数据侧我用 Spring Scheduler 和 GitHub REST API 定时采集项目，SystemTask 记录任务和分项进度，结合跨实例租约、失败重试、业务键幂等和内容哈希实现可恢复的增量更新。推荐时先按用户目的、Topic 别名和语言做确定性候选筛选，再从 Redis HNSW 检索 README 证据，做去重、来源加权和 Evidence Card 组装，限制模型只能从 candidateId/URL 白名单中推荐。
>
> AI 任务由 AiToolRouter 统一治理：Redis ZSET 和 Lua 控制全局并发名额，独占 token 管理模型槽位，租约续期和比较值释放避免僵尸任务，已完成任务直接回放持久化结果。付费和写操作通过 AgentAction 的 prepare/confirm 状态机和条件 SQL 保证并发幂等。这样系统既有 RAG 的事实依据，也有后端工程中的一致性、限流、重试和可观测性。”

## 14. 30 天学习与验证安排

| 天数 | 学习重点 | 必须留下的证据 |
|---|---|---|
| 1-2 | HTTP/HTTPS、DNS、Nginx、Cloudflare、Spring MVC 请求链路 | 画出 OrangeVC 一次请求图，能解释 502/504 |
| 3-5 | Java 基础、集合、异常、反射、Stream | 写候选去重和 Top-K 单元测试 |
| 6-9 | Spring IoC/AOP/事务/Boot 自动装配/MyBatis | 解释一个 Controller 到 Mapper 的真实调用链 |
| 10-13 | MySQL 索引、事务、MVCC、锁、EXPLAIN | 给状态迁移 SQL 做并发实验并保留日志 |
| 14-17 | Redis 数据结构、TTL、Lua、限流、租约 | 画出 AiToolRouter 租约状态图 |
| 18-20 | HNSW、Embedding、召回、重排、RAG 评测 | 跑 Recall@4、MRR、引用正确率和 P95 |
| 21-23 | Agent、Function Calling、AgentAction、工具治理 | 写出高风险动作状态机和错误处理表 |
| 24-25 | GitHub 采集、增量更新、榜单和幂等 | 模拟一次失败重试与内容未变化跳过 |
| 26-27 | Linux、systemd、Docker、日志和健康检查 | 从日志定位一次模拟故障 |
| 28-29 | 系统设计和成本治理 | 估算并发、队列、Token 和数据库压力 |
| 30 | 模拟面试 | 2 分钟项目介绍 + 20 个追问录音复盘 |

## 15. 自测清单：答不上就回到对应章节

### 平台与后端

- [ ] 为什么用 Spring Boot，而不是只用 Spring？
- [ ] Controller、Service、Mapper 为什么要分层？
- [ ] `@Transactional` 什么时候会失效？
- [ ] MyBatis `#{}` 和 `${}` 的区别？
- [ ] MySQL 为什么用 InnoDB？主键和联合索引怎么选？
- [ ] 条件 SQL 如何保证 `PREPARED -> QUEUED` 只成功一次？

### Redis 与并发

- [ ] ZSet 在排行榜和模型调度中分别怎么用？
- [ ] Redis Lua 为什么能保证脚本原子？
- [ ] 租约为什么必须 TTL？释放时为什么要比较 token？
- [ ] 缓存穿透、击穿、雪崩怎么区分？
- [ ] “支持上百名用户同时调用”的测量口径是什么？

### RAG 与 Agent

- [ ] 结构化候选和 RAG 各自解决什么问题？
- [ ] HNSW 为什么快，代价是什么？
- [ ] Recall@K、MRR、NDCG 分别怎么解释？
- [ ] 为什么 Evidence Card 能减少 Token，但不能自动提高召回率？
- [ ] AgentAction 和 AiToolRouter 的边界是什么？
- [ ] 模型重复调用如何通过结果回放避免？

### 部署与排障

- [ ] Cloudflare、Nginx、Spring Boot 各自负责哪一层？
- [ ] 502 与 504 的区别？
- [ ] WebSocket 断线、重复消息和鉴权怎么处理？
- [ ] 线上接口变慢时先看哪些日志和指标？
- [ ] systemd、健康检查和进程重启解决什么问题？

## 16. 原站资料索引

- 小林 coding 面试题总目录：https://www.xiaolincoding.com/interview/
- Java 基础：https://www.xiaolincoding.com/interview/java.html
- Spring：https://www.xiaolincoding.com/interview/spring.html
- MySQL：https://www.xiaolincoding.com/interview/mysql.html
- Redis：https://www.xiaolincoding.com/interview/redis.html
- 计算机网络：https://www.xiaolincoding.com/interview/network.html
- Docker：https://www.xiaolincoding.com/interview/docker.html
- 小林面试笔记（Agent/RAG/工具调用）：https://xiaolinnote.com/
- Agent 题库：https://xiaolinnote.com/ai/agent/agent_info.html
- RAG 题库：https://xiaolinnote.com/ai/rag/rag_info.html
- LLM 工具调用题库：https://xiaolinnote.com/ai/tools/tools_info.html

最后提醒：这份文档的目标是让你能从 OrangeVC 的真实代码讲出原理，而不是把所有八股包装成“项目做过”。面试中最有说服力的回答结构永远是：**问题场景 -> 设计选择 -> 关键实现 -> 失败边界 -> 测试数据**。
