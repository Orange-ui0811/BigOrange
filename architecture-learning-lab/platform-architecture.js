window.PlatformArchitectureData = {
  snapshot: {
    date: '2026-08-29',
    metrics: [
      ['3', 'Maven 模块'], ['2', 'Vue SPA'], ['59', 'Controller'], ['286', 'HTTP 映射'],
      ['46', 'Mapper'], ['37', 'Flyway 迁移'], ['60', '业务表'], ['238', '后端测试'],
    ],
    positioning: '模块化单体。双 SPA 通过 Nginx 访问单个 Spring Boot 运行单元，MySQL 保存业务事实，Redis 同时承担缓存、限流、租约、动态配置和向量检索。',
  },

  graphs: [
    {
      id: 'runtime', title: '运行时全景', subtitle: '从用户入口一直看到数据库和外部依赖',
      groups: ['终端', '边缘', '应用', '存储', '外部'],
      nodes: [
        { id:'browser', label:'用户浏览器', kind:'client', x:5, y:12, summary:'运行 orange-web，承载市场、交易、AI、画像和职业成长页面。', facts:['Vue 3 + Pinia + Axios','Token 只保存在 sessionStorage','3D 画像由 Three.js 在浏览器渲染'], files:['orange-web/src/router/index.js','orange-web/src/api/request.js','orange-web/src/stores/authStore.js'], failure:'静态资源或 API 不可达会阻断用户功能，但不会直接破坏数据库。', scale:'静态资源上 CDN，拆分超大 Chunk，保持前端无状态。' },
        { id:'admin-browser', label:'管理端浏览器', kind:'client', x:5, y:38, summary:'运行 orange-admin，负责审核、配置、采集和运营管理。', facts:['独立 Vue SPA','构建基路径 /admin/','与用户端使用不同 Token'], files:['orange-admin/src/router/index.js','orange-admin/src/api/request.js'], failure:'管理端不可用主要影响运营操作，用户公开浏览仍可继续。', scale:'先补齐管理端自动化测试，再扩大运营并发。' },
        { id:'device', label:'小智 / StackChan', kind:'client', x:5, y:68, summary:'通过连接码和设备凭证访问受控 Agent API。', facts:['设备凭证独立于用户 JWT','动作分 prepare 与 confirm','任务和结果可审计'], files:['orange-server/src/main/java/com/orangejd/server/controller/agent/AgentConnectController.java','orange-server/src/main/java/com/orangejd/server/controller/agent/AgentActionController.java'], failure:'Connector 离线只影响设备入口，不应拖垮网页业务。', scale:'设备数增长时拆分长连接与动作执行，保留统一权限模型。' },
        { id:'nginx', label:'Nginx 边缘层', kind:'edge', x:25, y:34, summary:'统一 TLS、域名、静态资源、反向代理、安全头和入口限流。', facts:['规范域名 orangevf.com','/admin/ 与用户端静态资源共用 Web 根','/ws/ 反向代理 WebSocket','/health 只允许本机'], files:['nginx.conf'], failure:'配置错误可能造成全站入口故障，错误 fallback 还会掩盖静态资源 404。', scale:'应用无状态化后配置 upstream；静态资源交给 CDN。' },
        { id:'web', label:'orange-web SPA', kind:'frontend', x:43, y:10, summary:'用户端 36 个 View、39 条路由，按 View、Composable、Store、API 分层。', facts:['Pinia 管理跨页面状态','Composable 承载页面流程','幂等 POST 自动复用 Idempotency-Key'], files:['orange-web/src/views','orange-web/src/composables','orange-web/src/stores','orange-web/src/api/request.js'], failure:'全局 15 秒超时不适合长 AI 请求，必须由具体流程覆盖。', scale:'路由级懒加载，拆 Three.js、ECharts 和趋势分析包。' },
        { id:'admin', label:'orange-admin SPA', kind:'frontend', x:43, y:28, summary:'管理端 20 个 View 和 20 条路由，面向审核、配置、运营和采集。', facts:['单独构建到 /admin/','Admin-Token','当前测试覆盖少于用户端'], files:['orange-admin/src/views','orange-admin/src/router/index.js'], failure:'审核和动态配置变更无法操作，但已有用户数据不应受损。', scale:'增加登录、分页、审核、AI 设置和采集运行 E2E 测试。' },
        { id:'server', label:'orange-server', kind:'backend', x:43, y:52, summary:'单个 Spring Boot 3.3.5 JAR，承载 API、WebSocket、调度、AI 与任务执行。', facts:['Java 21','模块化单体','systemd 管理','Controller 不允许直连 Mapper'], files:['orange-server/pom.xml','orange-server/src/main/java/com/orangejd/server','orange-server/src/main/resources/application-prod.yml'], failure:'单实例故障会影响全部后端能力，因此依赖健康检查、自动重启和发布回滚。', scale:'先无状态化和外置任务状态，再增加实例，最后才讨论微服务。' },
        { id:'ws', label:'WebSocket', kind:'backend', x:43, y:75, summary:'承载实时聊天与通知，连接前使用短期 Ticket 降低 Token 暴露。', facts:['Nginx /ws/ 升级连接','Redis 一次性消费 Ticket','消息事实仍落 MySQL'], files:['orange-server/src/main/java/com/orangejd/server/websocket','orange-server/src/main/java/com/orangejd/server/service/WebSocketTicketService.java'], failure:'连接断开影响实时性，客户端仍应通过 HTTP 重新同步消息和未读状态。', scale:'多实例前引入共享会话路由或消息背板。' },
        { id:'mysql', label:'MySQL 8.0', kind:'store', x:68, y:15, summary:'保存业务事实、状态机、任务、配额账本、审计和索引元数据。', facts:['仅监听 127.0.0.1','Flyway V1-V37 是结构唯一来源','当前与应用同机'], files:['orange-server/src/main/resources/db/migration','orange-server/src/main/resources/application-prod.yml'], failure:'不可用时核心读写和任务全部受阻，是当前主机级单点之一。', scale:'先做恢复演练、索引和慢查询治理，再考虑托管或热备。' },
        { id:'redis', label:'Redis + RediSearch', kind:'store', x:68, y:39, summary:'承担缓存、限流、动态 AI 配置、运行租约和向量检索。', facts:['Lua 保证租约原子性','Spring AI Redis VectorStore','配置故障可回退环境变量'], files:['orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolSlotLeaseService.java','orange-server/src/main/java/com/orangejd/server/service/RateLimitService.java'], failure:'故障影响面较大，但不应导致 MySQL 业务事实丢失；RAG 应能降级。', scale:'先建立容量指标，再按运行状态与向量数据拆实例。' },
        { id:'oss', label:'阿里云 OSS', kind:'store', x:68, y:63, summary:'私有桶保存用户媒体，独立前缀和凭证保存加密数据库备份。', facts:['媒体与备份权限隔离','服务端受控读取','备份使用 age 加密和 SHA-256'], files:['orange-server/src/main/java/com/orangejd/server/service/impl/OssObjectStorage.java','scripts/database'], failure:'媒体故障影响上传和展示；备份故障影响灾难恢复，不影响即时数据库写入。', scale:'生命周期策略、回源控制和独立备份账户。' },
        { id:'ai-provider', label:'AI 模型供应商', kind:'external', x:90, y:8, summary:'OpenAI 兼容 Chat Completions，Key 池忙时使用 tool_fallback。', facts:['最多 10 个 Gitee Key 槽','401/403 标记无效','429 尊重 Retry-After'], files:['orange-server/src/main/java/com/orangejd/server/foundation/external/ExternalAiClient.java','orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolRouter.java'], failure:'超时、限流、鉴权失败和格式错误按类型处理，不能在 Controller 里解析。', scale:'用真实 RPM、TPM、P95 和重试率压测，不用 Key 数推算用户数。' },
        { id:'github', label:'GitHub REST API', kind:'external', x:90, y:30, summary:'为榜单、仓库、Issue、README 和开源贡献流程提供源数据。', facts:['Search API 查询需 URL 编码','外部数据通过采集运行跟踪','README 进入向量库'], files:['orange-server/src/main/java/com/orangejd/server/foundation/external/GithubApiClient.java','orange-server/src/main/java/com/orangejd/server/service/leaderboard'], failure:'限流或解析失败必须使采集运行失败，不能发布假成功快照。', scale:'子任务化、限速、增量同步和断点恢复。' },
        { id:'identity-vendors', label:'SMTP + Turnstile', kind:'external', x:90, y:51, summary:'验证码邮件和机器人验证组成注册安全链路。', facts:['验证码有过期与频率控制','生产必须配置 Turnstile','外部失败与账号事务分离'], files:['orange-server/src/main/java/com/orangejd/server/service/EmailVerificationCodeService.java','orange-server/src/main/java/com/orangejd/server/service/TurnstileService.java'], failure:'供应商不可用会阻断注册或找回密码，需要明确错误和运维告警。', scale:'邮件异步化、发送队列和供应商降级。' },
        { id:'payment', label:'Epay + 飞书', kind:'external', x:90, y:73, summary:'Epay 完成充值和订单支付回调，飞书用于运维告警。', facts:['回调必须幂等','配置必须完整或关闭','告警按具体检查项解释'], files:['orange-server/src/main/java/com/orangejd/server/controller/app/EpayController.java','orange-server/src/main/java/com/orangejd/server/service/PaymentApplicationService.java'], failure:'外部支付成功而本地提交失败需要补偿和对账；重复回调不能重复入账。', scale:'独立支付边界、账本对账和回调事件表。' },
      ],
      edges: [
        ['browser','nginx','HTTPS'],['admin-browser','nginx','HTTPS'],['device','nginx','Device API'],
        ['nginx','web','静态资源'],['nginx','admin','/admin/'],['nginx','server','HTTP API'],['nginx','ws','连接升级'],
        ['web','server','/app/**'],['admin','server','/admin/**'],['ws','server','消息处理'],
        ['server','mysql','事务读写'],['server','redis','缓存/租约/向量'],['server','oss','媒体对象'],
        ['server','ai-provider','模型调用'],['server','github','采集'],['server','identity-vendors','验证'],['server','payment','回调/告警']
      ],
    },
    {
      id:'domains', title:'业务域全景', subtitle:'看清哪些模块拥有规则、状态和数据', groups:['入口业务','交易核心','智能能力','平台支撑'],
      nodes:[
        {id:'identity',label:'账号与身份',kind:'domain',x:5,y:12,summary:'注册登录、资料、JWT、设备凭证和管理员引导。',facts:['App/Admin JWT 密钥隔离','BCrypt 密码','邮箱验证码 + Turnstile'],files:['orange-server/src/main/java/com/orangejd/server/service/AuthService.java','orange-server/src/main/java/com/orangejd/server/security/ApiRoutePolicy.java'],tables:['account','user_profile','agent_device_credential'],failure:'错误白名单或密钥复用会造成越权。',scale:'统一身份主体、凭据版本和 Scope。'},
        {id:'content',label:'内容与市场',kind:'domain',x:5,y:39,summary:'作品、需求、分类、比赛、岗位、审核和公开展示。',facts:['草稿到审核再到公开','作者权限与可见性','审核证据保留'],files:['orange-server/src/main/java/com/orangejd/server/service/WorkService.java','orange-server/src/main/java/com/orangejd/server/service/RequirementService.java','orange-server/src/main/java/com/orangejd/server/service/AuditService.java'],tables:['work','requirement','category','audit_record'],failure:'状态不同步会误展示未审核内容。',scale:'搜索索引、审核队列、冷热内容策略。'},
        {id:'social',label:'互动与消息',kind:'domain',x:5,y:67,summary:'评论、收藏、历史、通知、私聊和 WebSocket。',facts:['HTTP 保存事实','WebSocket 提供实时性','未读状态可重新同步'],files:['orange-server/src/main/java/com/orangejd/server/service/ConversationService.java','orange-server/src/main/java/com/orangejd/server/websocket'],tables:['comment','favorite','conversation','message','notification'],failure:'连接中断不能导致消息事实丢失。',scale:'共享消息背板、分区会话和清理策略。'},
        {id:'trade',label:'报价与订单',kind:'domain',x:29,y:18,summary:'从沟通、报价到订单、支付阶段、交付和验收的状态机。',facts:['参与者权限校验','状态转换受 Service 控制','超时任务定期处理'],files:['orange-server/src/main/java/com/orangejd/server/service/impl/OrderServiceImpl.java','orange-server/src/main/java/com/orangejd/server/service/impl/QuoteServiceImpl.java'],tables:['quote','order','payment_stage','delivery'],failure:'并发状态覆盖、重复回调和外部成功本地失败。',scale:'乐观版本、幂等事件、账本对账，再考虑拆支付。'},
        {id:'quota',label:'配额与支付账本',kind:'domain',x:29,y:55,summary:'AI 定价、幂等扣减、退款、充值、兑换码与流水。',facts:['request_id 全局唯一','请求体和幂等键保存哈希','退款关联原操作'],files:['orange-server/src/main/java/com/orangejd/server/service/QuotaLedgerService.java','orange-server/src/main/java/com/orangejd/server/service/impl/QuotaServiceImpl.java'],tables:['quota_operation','quota_log','ai_service_pricing','top_up','redemption_code'],failure:'重复扣减或退款失败直接影响资产一致性。',scale:'账本只追加、对账作业、事务热点分离。'},
        {id:'ai',label:'AI 工具平台',kind:'domain',x:53,y:10,summary:'趋势、方案、体检、岗位、开源贡献和审核共用路由、租约、记录与重试。',facts:['Key 池全局并发上限 80','成功结果按 requestId 回放','调用日志不保存明文 Key'],files:['orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolRouter.java','orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiResultRecorder.java'],tables:['ai_tool_call_log','ai_result_record'],failure:'无限请求线程重试会占住线程和租约。',scale:'改为任务提交 + 轮询/推送，限制重试次数。'},
        {id:'knowledge',label:'知识库与 RAG',kind:'domain',x:53,y:38,summary:'通用文档、README、岗位、新手和职业项目共用 Redis VectorStore。',facts:['MySQL 保存元数据','Redis 保存向量','内容哈希判断重建'],files:['orange-server/src/main/java/com/orangejd/server/service/ProjectKnowledgeSyncService.java','orange-server/src/main/java/com/orangejd/server/service/impl/KnowledgeReindexService.java'],tables:['knowledge_document','project_knowledge_index_status'],failure:'VectorStore 不可用时应用仍应启动并降级非 RAG。',scale:'分离向量实例、批量嵌入和增量索引。'},
        {id:'discovery',label:'榜单、开源与职业',kind:'domain',x:53,y:67,summary:'GitHub 榜单、Issue、README、新手项目和职业项目的数据发现体系。',facts:['定时采集','候选筛选与评分','模型推荐链接必须来自真实候选'],files:['orange-server/src/main/java/com/orangejd/server/service/leaderboard','orange-server/src/main/java/com/orangejd/server/service/OpenSourceRefreshService.java'],tables:['leaderboard_snapshot','open_source_repository','open_source_issue','beginner_project','career_project'],failure:'整批同步流程过长，局部失败会影响运行状态。',scale:'拆分可并发子任务，保持 GitHub 速率限制。'},
        {id:'persona',label:'橙子画像工坊',kind:'domain',x:77,y:15,summary:'画像条目、素材、装备、DIY 参数、3D 展示和形象码。',facts:['浏览器渲染 Three.js','服务端保存结构化参数与 JSON 蓝图','门面下拆分多个内部 Service'],files:['orange-server/src/main/java/com/orangejd/server/service/PersonaWorkshopService.java','orange-web/src/composables/usePersonaWorkshop.js'],tables:['persona_profile_entry','persona_material','persona_equipment','persona_workshop_setting','persona_appearance_share'],failure:'无效蓝图或越权形象码会污染展示与账户边界。',scale:'保持参数化资产，不按用户生成独立模型文件。'},
        {id:'agent',label:'Agent 设备桥',kind:'domain',x:77,y:44,summary:'连接码换取设备凭证，模型动作通过 prepare/confirm 控制副作用。',facts:['设备认证独立','动作参数冻结','任务状态可追踪'],files:['orange-server/src/main/java/com/orangejd/server/service/AgentActionPreparationService.java','orange-server/src/main/java/com/orangejd/server/service/AgentActionExecutionService.java'],tables:['agent_device_link_code','agent_device_credential','agent_action_task'],failure:'直接执行自然语言动作会让模型不确定性进入付费副作用。',scale:'能力注册、细粒度权限、沙箱和审计策略。'},
        {id:'platform',label:'任务、采集与治理',kind:'foundation',x:77,y:72,summary:'System Task、Ingestion Run、架构规则、可观测性和发布运维支撑所有业务域。',facts:['任务租约与心跳','采集运行可重试取消','ArchUnit 强制依赖方向'],files:['orange-server/src/main/java/com/orangejd/server/foundation/task','orange-server/src/main/java/com/orangejd/server/foundation/ingestion','orange-server/src/test/java/com/orangejd/server/architecture/ArchitectureGovernanceTest.java'],tables:['system_task','ingestion_run','ingestion_run_item','ingestion_pipeline_lock'],failure:'任务重复执行、租约丢失和错误成功状态会污染多个业务域。',scale:'统一任务语义、可观测性和可水平扩展 Worker。'},
      ],
      edges:[['identity','content','作者身份'],['identity','social','参与者'],['content','social','互动对象'],['social','trade','促成报价'],['trade','quota','支付/入账'],['identity','quota','账户资产'],['ai','quota','计费/退款'],['ai','knowledge','检索上下文'],['ai','discovery','候选项目'],['persona','ai','画像上下文'],['discovery','knowledge','索引内容'],['agent','ai','模型决策'],['agent','quota','付费动作'],['platform','ai','任务/指标'],['platform','discovery','采集运行'],['platform','trade','超时任务']],
    },
    {
      id:'code', title:'代码分层与依赖', subtitle:'从 Vue 页面追到 Controller、Service、Mapper 和表', groups:['前端','接入','业务','基础设施','数据'],
      nodes:[
        {id:'view',label:'Vue View',kind:'frontend',x:4,y:16,summary:'组织页面布局和用户事件，不直接承载复杂业务协议。',facts:['36 个用户端 View','20 个管理端 View'],files:['orange-web/src/views','orange-admin/src/views'],failure:'把异步竞态和业务规则都写入页面会难以测试。',scale:'按 Feature 拆组件，流程进入 Composable。'},
        {id:'composable',label:'Composable',kind:'frontend',x:4,y:43,summary:'承载页面流程、加载态、错误态、请求竞态和销毁保护。',facts:['latest-request-wins','页面销毁保护','组合 Store 与 API'],files:['orange-web/src/composables/useTrendAnalysis.js','orange-web/src/composables/usePersonaWorkshop.js'],failure:'竞态未处理会让旧请求覆盖新状态。',scale:'按用户任务拆 Composable，不按后端接口数量拆。'},
        {id:'store-api',label:'Pinia Store / API',kind:'frontend',x:4,y:71,summary:'Store 保存跨页面状态，API Module 统一 HTTP 和幂等语义。',facts:['6 个核心 Store','Axios 默认 15 秒','幂等键按请求复用'],files:['orange-web/src/stores','orange-web/src/api/request.js'],failure:'长期保存 Token 或 AI 完整结果会扩大泄露面。',scale:'缓存边界和失效策略明确，长任务使用专用协议。'},
        {id:'nginx-code',label:'Nginx + 路由',kind:'edge',x:27,y:14,summary:'把静态页面、API、WebSocket 和安全文件路由到正确位置。',facts:['精确静态资源 404','安全头','入口限流'],files:['nginx.conf'],failure:'SPA fallback 配错会把缺失 JS 返回成 HTML。',scale:'upstream、CDN 与缓存规则。'},
        {id:'interceptors',label:'拦截器链',kind:'backend',x:27,y:40,summary:'Request ID、限流和 Admin/App/Agent 鉴权在 Controller 前执行。',facts:['公开路径集中在 ApiRoutePolicy','三种 Token 通道'],files:['orange-server/src/main/java/com/orangejd/server/config/WebMvcConfiguration.java','orange-server/src/main/java/com/orangejd/server/security/ApiRoutePolicy.java'],failure:'白名单散落或拦截顺序错误会造成越权或误拒绝。',scale:'统一策略、Scope 和可观测拒绝原因。'},
        {id:'controller',label:'Controller',kind:'backend',x:27,y:69,summary:'59 个入口类负责参数、可信身份、服务调用和 Result 包装。',facts:['286 个 HTTP 映射','禁止直接依赖 Mapper'],files:['orange-server/src/main/java/com/orangejd/server/controller'],failure:'业务规则落在 Controller 会绕开事务和复用边界。',scale:'保持薄入口，用应用服务承载用例。'},
        {id:'app-service',label:'Application Service',kind:'service',x:50,y:16,summary:'事务边界和业务用例编排，兼容门面可继续拆内部组件。',facts:['Service 入口声明事务','不依赖 Controller'],files:['orange-server/src/main/java/com/orangejd/server/service'],failure:'过大 Service 难以理解，但一次性重写风险更高。',scale:'沿修改点渐进拆策略、组装器和校验器。'},
        {id:'domain-components',label:'规则 / 策略 / 组装器',kind:'service',x:50,y:43,summary:'表达状态转换、候选筛选、结果清洗和展示组装。',facts:['TrendRecommendationSanitizer','PersonaSnapshotAssembler','BeginnerProjectScorer'],files:['orange-server/src/main/java/com/orangejd/server/service/TrendRecommendationSanitizer.java','orange-server/src/main/java/com/orangejd/server/service/PersonaSnapshotAssembler.java'],failure:'规则散在 SQL、Controller 与前端会形成多套事实。',scale:'显式规则对象、确定性测试和稳定输入输出。'},
        {id:'foundation',label:'Foundation',kind:'foundation',x:50,y:70,summary:'AI 路由、任务、采集、外部客户端和安全策略等跨业务基础设施。',facts:['不能反向依赖业务 Service','提供可复用运行语义'],files:['orange-server/src/main/java/com/orangejd/server/foundation'],failure:'基础设施反向引用业务会形成循环和隐式耦合。',scale:'保持窄接口，业务通过适配器使用。'},
        {id:'mapper',label:'MyBatis Mapper',kind:'data',x:74,y:13,summary:'46 个 Mapper 把业务读写意图转换为 SQL。',facts:['不依赖 Service 或 Controller','事务由上层 Service 控制'],files:['orange-server/src/main/java/com/orangejd/server/mapper'],failure:'复杂规则藏在 SQL 会让调用方难以理解。',scale:'索引、批量读写和查询专用模型。'},
        {id:'redis-adapter',label:'Redis / VectorStore',kind:'data',x:74,y:38,summary:'通过模板、Lua 和 Spring AI 适配运行状态与向量。',facts:['租约原子操作','RAG 可降级'],files:['orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolSlotLeaseService.java','orange-server/src/main/java/com/orangejd/server/config'],failure:'把 Redis 当唯一业务事实来源会产生不可恢复状态。',scale:'分实例和容量治理。'},
        {id:'external-client',label:'外部客户端',kind:'external',x:74,y:66,summary:'隔离 AI、GitHub、OSS、支付、邮件和 Turnstile 协议。',facts:['错误分类','超时和重试策略','外部 URL 安全策略'],files:['orange-server/src/main/java/com/orangejd/server/foundation/external','orange-server/src/main/java/com/orangejd/server/service/impl/OssObjectStorage.java'],failure:'供应商细节泄漏进业务层会使切换和测试困难。',scale:'适配器、熔断、配额和多供应商路由。'},
        {id:'common',label:'orange-common',kind:'module',x:94,y:16,summary:'Result、JWT、上下文和公共工具，不承载单一业务流程。',facts:['被 server 依赖','保持通用语义'],files:['orange-common/src/main/java'],failure:'业务类下沉会让模块成为公共垃圾场。',scale:'只保留跨域稳定抽象。'},
        {id:'pojo',label:'orange-pojo',kind:'module',x:94,y:43,summary:'Entity、DTO、VO 等传输和持久化模型。',facts:['被 server 依赖','不同边界使用不同模型'],files:['orange-pojo/src/main/java'],failure:'直接把 Entity 暴露给前端会耦合数据库结构。',scale:'明确 DTO、VO 和 Entity 转换。'},
        {id:'flyway',label:'Flyway + MySQL',kind:'store',x:94,y:70,summary:'V1-V37 是数据库结构唯一执行来源，MySQL 保存最终事实。',facts:['已执行迁移不可修改','新结构只能新增版本'],files:['orange-server/src/main/resources/db/migration'],failure:'改写历史迁移会让环境结构分叉。',scale:'在线迁移、回填和回滚预案。'},
      ],
      edges:[['view','composable','用户事件'],['composable','store-api','状态/API'],['store-api','nginx-code','HTTP'],['nginx-code','interceptors','代理'],['interceptors','controller','放行身份'],['controller','app-service','调用用例'],['app-service','domain-components','业务规则'],['app-service','mapper','事务读写'],['app-service','foundation','跨域能力'],['foundation','redis-adapter','运行状态'],['foundation','external-client','供应商协议'],['mapper','flyway','SQL/表'],['app-service','common','Result/上下文'],['app-service','pojo','DTO/VO/Entity'],['controller','common','统一响应'],['mapper','pojo','Entity']],
    },
    {
      id:'reliability', title:'数据、异步与运维', subtitle:'看清任务、租约、发布、监控和恢复', groups:['数据事实','异步执行','发布运行','观测恢复'],
      nodes:[
        {id:'flyway-db',label:'Flyway / MySQL',kind:'store',x:4,y:14,summary:'37 个顺序迁移定义约 60 张业务表，MySQL 保存业务和任务事实。',facts:['迁移不可改写','事务一致性','慢查询阈值 1 秒'],files:['orange-server/src/main/resources/db/migration'],failure:'迁移失败阻止安全启动；主机故障同时影响应用和数据库。',scale:'恢复演练、读写治理、托管或热备。'},
        {id:'quota-ledger',label:'幂等配额账本',kind:'data',x:4,y:47,summary:'防止重复请求、重试和结果回放造成重复扣费。',facts:['request_id 唯一','幂等键哈希唯一约束','退款关联原操作'],files:['orange-server/src/main/java/com/orangejd/server/service/QuotaLedgerService.java','orange-server/src/main/resources/db/migration/V37__harden_quota_ledger.sql'],failure:'请求哈希不一致必须拒绝重放。',scale:'只追加审计、异步对账、热点账户治理。'},
        {id:'vector-data',label:'知识元数据 + 向量',kind:'data',x:4,y:75,summary:'MySQL 记录文档与索引状态，Redis VectorStore 保存向量。',facts:['内容哈希去重','删除时同步清理','默认 5 分钟扫描'],files:['orange-server/src/main/java/com/orangejd/server/service/ProjectKnowledgeSyncService.java'],failure:'两边不同步会产生幽灵检索结果或漏召回。',scale:'增量队列、批量嵌入、索引重建作业。'},
        {id:'system-task',label:'System Task',kind:'foundation',x:29,y:13,summary:'数据库持久化任务中心，提供幂等入队、claim、租约、心跳、恢复和退避。',facts:['2 秒扫描','每批最多 20','120 秒租约','30 秒心跳'],files:['orange-server/src/main/java/com/orangejd/server/foundation/task/SystemTaskService.java','orange-server/src/main/java/com/orangejd/server/foundation/task/SystemTaskMapper.java'],failure:'旧 Worker 与新 Worker 可能重叠，Handler 必须幂等。',scale:'增加独立 Worker、分片领取和队列指标。'},
        {id:'ingestion',label:'Ingestion Run',kind:'foundation',x:29,y:47,summary:'把榜单、Issue、README、新手和职业采集组织成可追踪运行。',facts:['运行、阶段、条目','流水线跨实例锁','支持取消与重试'],files:['orange-server/src/main/java/com/orangejd/server/foundation/ingestion/IngestionCoordinatorService.java','orange-server/src/main/java/com/orangejd/server/foundation/ingestion/IngestionRunService.java'],failure:'采集项失败不能把整批标为成功。',scale:'拆阶段子任务、限速并行、断点续跑。'},
        {id:'ai-lease',label:'AI 租约与重试',kind:'foundation',x:29,y:75,summary:'Redis Lua 原子获取全局容量和 Key 槽，定期续租并按错误类型重试。',facts:['槽位独占','30 秒续租','结果回放','0 次上限代表无限重试'],files:['orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolRouter.java','orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolSlotLeaseService.java'],failure:'线程内无限重试长期占据线程和租约。',scale:'迁入持久化任务，有限重试和熔断。'},
        {id:'ci',label:'CI 质量门禁',kind:'ops',x:54,y:12,summary:'Java、Flyway、两个前端、预算、E2E 和密钥扫描共同保护合并。',facts:['mvn clean verify','空库执行全部迁移','Playwright Chromium','Gitleaks'],files:['.github/workflows/ci.yml'],failure:'管理端测试过少会形成覆盖盲区。',scale:'补高风险业务流程和契约测试。'},
        {id:'release',label:'构建与发布包',kind:'ops',x:54,y:42,summary:'统一构建 JAR 和双 SPA，生成 Git、文件哈希、迁移清单与配置指纹。',facts:['禁止手工复制单个 dist','校验 SPA 资源存在'],files:['scripts/build-release.sh'],failure:'手工覆盖可能让用户端覆盖管理端或 HTML 引用旧资源。',scale:'制品仓库、签名和不可变发布。'},
        {id:'install',label:'安装与自动回滚',kind:'ops',x:54,y:72,summary:'备份旧版本，校验候选 Nginx，切换后验证站点和健康，失败自动恢复。',facts:['JAR/前端/Nginx 一起切换','发布后业务验收'],files:['scripts/install-release.sh'],failure:'含不可逆数据库迁移时，文件回滚不等于数据回滚。',scale:'蓝绿发布、迁移兼容窗口和渐进流量。'},
        {id:'systemd',label:'systemd 运行单元',kind:'ops',x:77,y:14,summary:'以 admin 用户运行单个 JAR，限制写目录并在失败后重启。',facts:['JVM Xmx 2048m','环境变量独立文件','NoNewPrivileges'],files:['deploy/systemd','scripts/install-release.sh'],failure:'单进程重启期间全部 API 短暂不可用。',scale:'无状态化后多实例与滚动发布。'},
        {id:'health',label:'三类健康信号',kind:'observe',x:77,y:45,summary:'区分应用存活、依赖健康和业务健康，避免错误归因。',facts:['/health/detail','Prometheus','请求 ID','AI/任务/采集记录'],files:['orange-server/src/main/java/com/orangejd/server/controller/HealthController.java','orange-server/src/main/resources/application-prod.yml'],failure:'只看进程存活会漏掉 AI 失败、采集陈旧和备份过期。',scale:'SLO、分层告警和分布式 Tracing。'},
        {id:'backup',label:'加密备份与恢复',kind:'observe',x:77,y:75,summary:'每日 mysqldump、gzip、age 加密、SHA-256、OSS 回读校验和保留策略。',facts:['本地 7 天','日备份 30 天','月备份 400 天'],files:['scripts/database','docs/当前生产部署与运维手册.md'],failure:'备份存在不代表可恢复，必须做恢复演练。',scale:'异地副本、恢复时间目标和自动演练。'},
        {id:'single-host',label:'当前单机边界',kind:'risk',x:95,y:44,summary:'应用、MySQL 和 Redis 当前仍在同一服务器，成本低但存在主机级单点。',facts:['适合当前规模','运维简单','故障域集中'],files:['docs/OrangeJD系统架构设计说明书.md'],failure:'磁盘、宿主机或网络故障会同时影响多个组件。',scale:'先证明恢复能力，再按瓶颈迁移数据库、Redis 或应用。'},
      ],
      edges:[['flyway-db','quota-ledger','事务事实'],['flyway-db','system-task','任务记录'],['flyway-db','ingestion','运行记录'],['vector-data','ingestion','索引任务'],['system-task','ingestion','执行阶段'],['system-task','ai-lease','长任务调用'],['ci','release','通过后构建'],['release','install','发布制品'],['install','systemd','重启切换'],['systemd','health','运行指标'],['health','backup','新鲜度检查'],['flyway-db','backup','加密导出'],['systemd','single-host','同机'],['flyway-db','single-host','同机'],['ai-lease','health','调用指标']],
    },
  ],

  learningPath: [
    { title:'先认边界', body:'从运行时全景确认哪些是进程、哪些是代码模块、哪些是外部服务。', target:'runtime' },
    { title:'再认所有权', body:'从业务域图确认规则、状态和表由谁拥有。', target:'domains' },
    { title:'沿代码下钻', body:'按 View 到表的依赖方向阅读，不从文件名随机跳转。', target:'code' },
    { title:'最后看可靠性', body:'把租约、幂等、重试、发布和恢复串成系统运行模型。', target:'reliability' },
  ],

  codeCatalog: [
    { group:'接入与安全', items:[
      ['MVC 拦截器链','orange-server/src/main/java/com/orangejd/server/config/WebMvcConfiguration.java','Request ID、限流与三类鉴权的顺序'],
      ['公开路由策略','orange-server/src/main/java/com/orangejd/server/security/ApiRoutePolicy.java','集中维护 App/Admin/Agent 白名单'],
      ['前端 HTTP','orange-web/src/api/request.js','Token、错误、超时和幂等键'],
    ]},
    { group:'AI 与配额', items:[
      ['AI 路由','orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolRouter.java','回放、租约、重试、记录与释放'],
      ['槽位租约','orange-server/src/main/java/com/orangejd/server/foundation/aitool/AiToolSlotLeaseService.java','Redis Lua 原子容量控制'],
      ['配额账本','orange-server/src/main/java/com/orangejd/server/service/QuotaLedgerService.java','幂等扣减与请求哈希'],
      ['趋势编排','orange-server/src/main/java/com/orangejd/server/service/TrendAnalysisService.java','V1/V2 与工具池入口'],
    ]},
    { group:'任务与采集', items:[
      ['持久化任务','orange-server/src/main/java/com/orangejd/server/foundation/task/SystemTaskService.java','入队、claim、心跳、恢复和退避'],
      ['采集协调','orange-server/src/main/java/com/orangejd/server/foundation/ingestion/IngestionCoordinatorService.java','运行创建、任务入队、重试与取消'],
      ['采集租约','orange-server/src/main/java/com/orangejd/server/foundation/ingestion/IngestionRunService.java','跨实例流水线锁'],
    ]},
    { group:'复杂业务门面', items:[
      ['画像门面','orange-server/src/main/java/com/orangejd/server/service/PersonaWorkshopService.java','保持 API 稳定并拆内部服务'],
      ['交易状态机','orange-server/src/main/java/com/orangejd/server/service/impl/OrderServiceImpl.java','参与者、状态转换和交付'],
      ['Agent 动作','orange-server/src/main/java/com/orangejd/server/service/AgentActionService.java','prepare/confirm/execute 受控副作用'],
    ]},
    { group:'交付与治理', items:[
      ['架构规则','orange-server/src/test/java/com/orangejd/server/architecture/ArchitectureGovernanceTest.java','防止依赖方向退化'],
      ['构建发布','scripts/build-release.sh','统一 JAR、双 SPA 和清单'],
      ['安装回滚','scripts/install-release.sh','候选校验、切换和恢复'],
      ['数据库结构','orange-server/src/main/resources/db/migration','V1-V37 唯一迁移来源'],
    ]},
  ],

  interviewGuides: {
    server: {
      pitch:'OrangeVC 当前采用模块化单体。一个 Spring Boot 进程承载 API、WebSocket、调度、AI 和任务执行，但用 Maven 模块、ArchUnit、Service 事务和 foundation 边界控制内部耦合。',
      decision:'当前主要矛盾是业务边界、任务可靠性和外部调用治理，而不是独立扩缩容。单体保留本地事务，减少部署和分布式一致性成本。',
      flow:'Nginx 把请求转给 server，请求先经过 Request ID、限流和对应身份拦截器，再进入 Controller、Service、Mapper 或基础设施适配器，最终统一包装响应。',
      invariants:['Controller 不直接依赖 Mapper','事务边界位于 Service 入口','foundation 不反向依赖业务 Service','业务事实最终写入 MySQL'],
      diagnostics:['systemd 进程与最近重启原因','/health/detail 的 MySQL、Redis、VectorStore','请求 ID 对应的结构化日志','线程、数据库连接和 Redis 延迟'],
      questions:['为什么现在不拆微服务？','怎样让应用具备水平扩展条件？','单实例故障如何降低影响？']
    },
    nginx: {
      pitch:'Nginx 不是简单反向代理，它同时定义 OrangeVC 的 TLS、安全头、规范域名、双 SPA 静态资源边界、WebSocket 升级和第一层限流。',
      decision:'把跨应用的一致入口规则放在边缘层，避免两个 SPA 和后端各自处理 HTTPS、缓存与安全头。',
      flow:'浏览器先取静态 HTML 和资源；/app、/admin、/agent 请求转到 server；/ws 完成连接升级；缺失静态资源必须精确返回 404。',
      invariants:['用户端与管理端资源路径不能互相覆盖','API 与静态 fallback 必须分开','/health 只允许本机','HTTP 与 www 统一跳转规范域名'],
      diagnostics:['Nginx access/error log','HTML 实际引用的 JS/CSS 文件','响应 Content-Type 与状态码','候选配置校验结果'],
      questions:['SPA fallback 为什么可能导致白屏难排查？','多实例后 upstream 怎样配置？','应用限流和 Nginx 限流有什么区别？']
    },
    redis: {
      pitch:'Redis 在 OrangeVC 中不是单纯缓存，而是运行时控制平面，承担限流、AI 租约、动态配置和向量检索，因此必须分别定义每种用途的故障和重建语义。',
      decision:'高频短状态和原子租约适合 Redis，向量检索由 RediSearch 提供；永久业务事实仍留在 MySQL。',
      flow:'请求通过 Lua 原子修改计数或租约；AI 配置从 Redis 读取并可回退环境变量；RAG 从 VectorStore 检索 metadata 与向量。',
      invariants:['Redis 不能成为不可恢复的唯一业务事实','租约操作必须原子','向量故障不能被误报为 MySQL 故障','动态 Key 配置必须加密'],
      diagnostics:['/health/detail 的 Redis 与 VectorStore 分项','租约 Key 的 TTL 和持有者','内存、延迟和驱逐指标','环境配置回退是否生效'],
      questions:['为什么 Redis 故障影响面这么大？','何时需要拆分 Redis 实例？','缓存、租约和向量的数据恢复策略有何不同？']
    },
    mysql: {
      pitch:'MySQL 是平台事实源，同时保存业务状态机、配额账本、持久化任务、采集运行和审计记录，Flyway V1-V37 是唯一结构来源。',
      decision:'当前模块化单体通过本地数据库事务保证跨表一致性，避免过早引入分布式事务。',
      flow:'Service 开启事务，Mapper 执行 SQL；任务 Worker 也通过数据库 claim 和租约更新状态；备份任务定期导出、加密并上传 OSS。',
      invariants:['已执行的 Flyway 迁移不得修改','写业务在事务中完成','幂等唯一约束必须由数据库兜底','备份必须通过恢复演练验证'],
      diagnostics:['连接池和慢查询','Flyway 历史','锁等待和事务回滚','最近加密备份新鲜度'],
      questions:['为什么不用 Redis 保存订单状态？','数据库和应用同机会有什么风险？','怎样无停机执行大表迁移？']
    },
    'app-service': {
      pitch:'Application Service 是一个业务用例的事务与编排边界。Controller 只接参数和可信身份，Service 负责规则、状态转换、持久化和外部能力协调。',
      decision:'这样既防止 Controller 变成业务脚本，也避免 Mapper 或外部客户端决定业务流程。复杂门面可继续拆组装器、策略和校验器。',
      flow:'Controller 传入 DTO 和身份，Service 校验所有权与当前状态，调用规则组件和 Mapper，必要时协调 Redis 或外部客户端，最后返回 VO。',
      invariants:['同一业务用例只有一个明确入口','事务内只做需要强一致的写入','外部失败与本地状态转换有补偿策略','Service 不依赖 Controller'],
      diagnostics:['业务异常与请求 ID','事务回滚日志','状态转换前后数据','外部调用记录和补偿结果'],
      questions:['Service 太大时你会怎样拆？','领域服务和应用服务如何区分？','为什么不把事务放 Controller？']
    },
    controller: {
      pitch:'OrangeVC 有 59 个 Controller 和 286 个 HTTP 映射，但通过架构测试禁止 Controller 直接依赖 Mapper，保持接入层轻量。',
      decision:'HTTP 参数、Token 和 Result 都是接入协议；业务规则必须能脱离 HTTP 被任务、Agent 或测试复用。',
      flow:'拦截器建立可信身份，Controller 做参数校验和 DTO 转换，调用 Service，再由全局异常处理形成稳定响应。',
      invariants:['不直接访问 Mapper','不解析供应商异常细节','不自行开启复杂异步流程','只使用可信上下文中的账户身份'],
      diagnostics:['路由是否进入正确鉴权通道','参数校验错误','业务异常映射','X-Request-Id'],
      questions:['薄 Controller 的标准是什么？','哪些校验应该留在 Controller？','如何防止新增接口遗漏鉴权？']
    },
    interceptors: {
      pitch:'接入链固定为 Request ID、限流、Admin/App/Agent 对应鉴权，再进入 Controller；公开路径由 ApiRoutePolicy 集中维护。',
      decision:'集中式路径策略减少新 Controller 在多处复制白名单的风险，同时让三类凭证保持隔离。',
      flow:'RequestIdInterceptor 先建立追踪标识，RateLimitInterceptor 执行入口保护，随后按路径选择 Token 解析并写入可信上下文。',
      invariants:['Admin、App、Device 凭证不能跨通道复用','公开路径只能在一个策略源维护','限流拒绝也必须带请求 ID'],
      diagnostics:['拦截器顺序','ApiRoutePolicy 匹配结果','Token 类型和过期状态','限流 Redis 计数'],
      questions:['为什么 Request ID 要最先执行？','Nginx 限流后为什么还要应用限流？','路径白名单有哪些维护风险？']
    },
    'ai-lease': {
      pitch:'AI 工具路由把结果回放、全局容量、Key 槽独占、续租、错误分类、重试和调用记录封装成统一运行时。',
      decision:'业务服务不应该各自实现供应商并发和重试。Redis Lua 让全局容量检查与槽位占用成为原子操作。',
      flow:'先按 requestId 查成功结果，再获取全局租约和 Key 槽，周期续租，执行模型，记录尝试和结果，最后在 finally 中释放租约。',
      invariants:['同一 Key 槽同一时刻只允许一个请求','成功结果必须可回放','释放只能由租约持有者执行','401、429、超时不能使用同一策略'],
      diagnostics:['ai_tool_call_log','槽位 TTL 与状态','Retry-After 和重试次数','结果保存率和退款记录'],
      questions:['Lua 解决了什么竞态？','为什么 0 次重试上限反而危险？','模型成功但保存失败怎样处理？']
    },
    'system-task': {
      pitch:'System Task 是基于 MySQL 的轻量持久化任务中心，提供业务键幂等入队、数据库 claim、120 秒租约、30 秒心跳和过期恢复。',
      decision:'当前任务规模不需要引入独立 MQ，但任务状态必须在进程重启后仍可恢复，并支持管理端观察和取消。',
      flow:'业务先写 system_task，Worker 每 2 秒扫描并原子 claim，线程池执行 Handler，心跳续租，成功或失败后更新结果和下次执行时间。',
      invariants:['claim 必须带状态条件','Handler 必须幂等','取消是协作式的','租约恢复意味着至少一次执行'],
      diagnostics:['taskId 和 businessKey','status、attempts、next_run_at','locked_by 与 lease_until','Handler 的外部副作用记录'],
      questions:['为什么租约不能保证恰好一次？','什么时候应迁移到 MQ？','如何防止旧 Worker 与新 Worker 重复执行？']
    },
    ingestion: {
      pitch:'Ingestion Run 是外部数据流水线的控制面，记录一次运行、阶段和条目，并通过 pipeline lock 防止跨实例重复刷新。',
      decision:'榜单、Issue、README 等长流程不能只靠一个定时方法和日志判断成功，需要可追踪、可取消、可重试的运行模型。',
      flow:'管理端或调度创建 run，获取流水线租约，再按 pipelineType 入队 System Task；任务持续上报进度和心跳，最后完成或释放锁。',
      invariants:['同一流水线同一时间只有一个有效运行','局部失败不能误报整批成功','重试创建新运行并保留来源关系','取消必须传播到任务'],
      diagnostics:['ingestion_run 状态与进度','run_item 失败阶段','pipeline_lock 的 owner 和 TTL','关联 system_task'],
      questions:['Ingestion Run 和 System Task 有什么区别？','如何拆分长时间完整刷新？','跨实例锁过期怎样避免并发运行？']
    },
    'quota-ledger': {
      pitch:'配额账本用 requestId、幂等作用域、键哈希和请求哈希防止重复扣费，并让退款与原操作形成可审计关联。',
      decision:'前端重试、网关重试和 AI 结果回放都可能重复提交，资产一致性必须由服务事务与数据库唯一约束共同保证。',
      flow:'服务构造规范请求哈希，先查或创建 quota_operation，再写扣减流水；失败退款使用派生 requestId 并关联原 debit log。',
      invariants:['同一幂等键不能对应不同请求体','余额与流水在同一事务更新','退款不能超过原扣减','公开流水不暴露客户端幂等键'],
      diagnostics:['quota_operation 状态','request_hash 是否一致','quota_log 的原操作关联','AI 结果记录和退款日志'],
      questions:['为什么前端和后端都要做幂等？','请求哈希解决什么攻击或错误？','数据库唯一约束为何仍不可省？']
    },
    agent: {
      pitch:'Agent 设备桥把自然语言模型视为不可信决策者，通过 prepare、用户确认和 confirm 三阶段把副作用限制在受控服务中。',
      decision:'模型可以提出动作，但不能自行决定最终参数、价格和权限；确认前冻结动作快照，执行时再次校验身份和状态。',
      flow:'设备换取独立凭证，调用 prepare 生成待确认任务，用户查看冻结参数，confirm 后由执行服务完成动作并记录结果。',
      invariants:['设备凭证与用户 JWT 隔离','确认后参数不可被模型修改','付费动作必须明确确认','所有副作用可审计'],
      diagnostics:['设备 credentialId','agent_action_task 状态','prepare 快照与 confirm 输入','最终业务 requestId 和账本'],
      questions:['为什么 Agent 不能直接调用 Service？','prepare/confirm 与两阶段提交有什么异同？','怎样设计 Tool 权限和沙箱？']
    },
    common: {
      pitch:'orange-common 是被 server 依赖的项目公共模块，放置 Result、JWT、上下文和公共工具。它是编译边界，不是独立服务。',
      decision:'稳定的跨域契约与工具独立编译，可以阻止它们反向依赖 Controller、数据库和 AI 运行时。',
      flow:'根 Maven 工程先编译 common，server 在编译和打包时依赖它，最终类仍进入同一个 Spring Boot 可执行 JAR。',
      invariants:['不能放单一业务流程','不能反向依赖 server','API 变化要考虑所有使用者','公共并不等于可以随意堆工具'],
      diagnostics:['pom 依赖方向','模块编译失败的调用方','公共 API 的引用范围'],
      questions:['common 会不会变成垃圾场？','为什么它仍依赖 Spring、JWT、POI？','什么内容不应该放 common？']
    },
    pojo: {
      pitch:'orange-pojo 集中 Entity、DTO 和 VO，使传输与持久化模型不依赖 server 的 Web、数据库连接和 AI 基础设施。它同样不是独立服务。',
      decision:'模型作为低层契约被 server 使用，单独模块可以在编译期阻止模型反向引用业务实现。',
      flow:'pojo 先编译为普通 JAR，server 的 Controller、Service 和 Mapper 引用这些类型，Spring Boot 打包时把它纳入最终产物。',
      invariants:['DTO、VO、Entity 语义要区分','模型不能调用 Service','数据库 Entity 不直接成为公开 API 契约'],
      diagnostics:['类型被哪些层引用','序列化和校验错误','Entity 与迁移字段是否一致'],
      questions:['为什么不把所有模型都放 server？','DTO 和 VO 有什么区别？','什么时候应该按业务域拆模型包？']
    },
    flyway: {
      pitch:'Flyway 迁移目录是数据库结构的唯一执行来源，V1 是基线，V2-V37 只做增量，已执行版本不可修改。',
      decision:'让本地、测试和生产用同一迁移序列重建结构，避免手工 SQL 和快照形成多个事实源。',
      flow:'应用启动时读取迁移历史，按版本执行未应用 SQL；CI 还会在 MySQL 空库上顺序执行全部迁移。',
      invariants:['历史迁移不可改写','新结构必须新增版本','代码部署要与迁移兼容','大回填需要独立策略'],
      diagnostics:['flyway_schema_history','失败迁移的具体 SQL','应用版本与数据库版本','迁移前后的索引和锁'],
      questions:['为什么不能修改已上线迁移？','如何设计可回滚迁移？','先发代码还是先改数据库？']
    }
  },

  failureScenarios: [
    { title:'Redis 整体不可用', affects:['限流','AI 租约','动态配置','RAG 向量'], survives:['MySQL 业务事实','静态前端','环境变量 AI 配置回退'], first:'先看 /health/detail 的 Redis 与 VectorStore，再区分普通业务、AI 和 RAG 的影响。', improve:'把向量与运行状态分实例，明确每项能力的降级语义。' },
    { title:'AI 供应商持续 429', affects:['模型请求延迟','Key 槽状态','请求线程与租约'], survives:['非 AI 市场和交易','已有成功结果回放'], first:'看 ai_tool_call_log、Retry-After、槽位状态、兜底比例和线程占用。', improve:'限制重试次数，把长重试迁入 System Task，并加熔断。' },
    { title:'Worker 在租约过期后仍执行', affects:['任务可能至少执行两次','外部副作用可能重复'], survives:['幂等 Handler 和业务唯一约束保护的操作'], first:'用 taskId、businessKey、locked_by 和业务请求 ID 对齐两次执行。', improve:'所有 Handler 幂等，副作用使用业务键和结果回放。' },
    { title:'支付成功但本地事务失败', affects:['外部资金与本地余额不一致'], survives:['幂等回调事件和供应商订单号可用于重放'], first:'核对 Epay 订单、回调事件表、top_up 与 quota_log。', improve:'回调事件持久化、可重放应用服务和定期对账。' },
    { title:'发布后静态资源 404', affects:['某个 SPA 或页面白屏'], survives:['后端和数据库可能完全正常'], first:'核对 HTML 引用资源、构建 manifest、Nginx 精确 404 与发布包哈希。', improve:'只使用统一 build-release/install-release，不手工覆盖 dist。' },
    { title:'单机磁盘或宿主机故障', affects:['应用、MySQL、Redis 和本地日志同时中断'], survives:['OSS 媒体和加密备份'], first:'按恢复优先级重建 MySQL、配置、JAR、双 SPA、Nginx 和 Redis 向量。', improve:'定期恢复演练，逐步外置有状态组件和引入多实例。' },
  ],
}
