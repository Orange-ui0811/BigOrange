# 证据不足分流：离线验收

通过 218 项检查，错误、失败、跳过均为0；20条新行为回复由真实业务路由及LangGraph4j执行器各生成一遍。模型为测试替身，真实外部调用0。

| 用户消息 | 回复状态 | 缺口分类 | 下一步 | 实际追问 |
|---|---|---|---|---|
| 根据项目文档说明部署要求 | KNOWLEDGE_UNAVAILABLE | VERIFICATION_UNAVAILABLE | RETRY_VERIFICATION | 无 |
| 根据项目文档，Demo的最低机器内存是多少？ | REFUSE_INSUFFICIENT_EVIDENCE | PROJECT_FACTS_UNVERIFIED | VERIFY_PROJECT_FACTS | 无 |
| 帮我推荐能部署到我电脑上的知识库项目 | FOLLOW_UP | USER_INPUT_REQUIRED | ASK_USER | 你准备使用什么系统、可用内存大约多少？ |
| 想找知识库项目，但还没确定怎么用 | FOLLOW_UP | USER_INPUT_REQUIRED | ASK_USER | 你准备直接部署使用，还是二次开发？ |
| 根据项目文档，免费社区版是否自带单点登录？ | REFUSE_NO_MATCH | PROJECT_FACTS_UNVERIFIED | VERIFY_PROJECT_FACTS | 无 |
| 根据文档，Demo的安装命令和最低内存分别是什么？ | PARTIALLY_SUPPORTED_EXTRACTS | PARTIALLY_SUPPORTED | VERIFY_PROJECT_FACTS | 无 |
| 根据文档，Demo 构建要求哪个Go版本？ | SUPPORTED_EXTRACTS | SUPPORTED | ANSWER_VERIFIED_FACTS | 无 |
| 根据文档，Demo要求哪个Go版本？ | PARTIALLY_SUPPORTED_EXTRACTS | PARTIALLY_SUPPORTED | VERIFY_PROJECT_FACTS | 无 |
| 根据知识库文档，Demo要求哪个Go版本？ | SUPPORTED_EXTRACTS | SUPPORTED | ANSWER_VERIFIED_FACTS | 无 |
| 根据知识库文档，Demo是什么许可证？ | SUPPORTED_EXTRACTS | SUPPORTED | ANSWER_VERIFIED_FACTS | 无 |
| 根据项目文档说明部署要求 | KNOWLEDGE_UNAVAILABLE | VERIFICATION_UNAVAILABLE | RETRY_VERIFICATION | 无 |
| 根据项目文档，Demo的最低机器内存是多少？ | REFUSE_INSUFFICIENT_EVIDENCE | PROJECT_FACTS_UNVERIFIED | VERIFY_PROJECT_FACTS | 无 |
| 帮我推荐能部署到我电脑上的知识库项目 | FOLLOW_UP | USER_INPUT_REQUIRED | ASK_USER | 你准备使用什么系统、可用内存大约多少？ |
| 想找知识库项目，但还没确定怎么用 | FOLLOW_UP | USER_INPUT_REQUIRED | ASK_USER | 你准备直接部署使用，还是二次开发？ |
| 根据项目文档，免费社区版是否自带单点登录？ | REFUSE_NO_MATCH | PROJECT_FACTS_UNVERIFIED | VERIFY_PROJECT_FACTS | 无 |
| 根据文档，Demo的安装命令和最低内存分别是什么？ | PARTIALLY_SUPPORTED_EXTRACTS | PARTIALLY_SUPPORTED | VERIFY_PROJECT_FACTS | 无 |
| 根据文档，Demo 构建要求哪个Go版本？ | SUPPORTED_EXTRACTS | SUPPORTED | ANSWER_VERIFIED_FACTS | 无 |
| 根据文档，Demo要求哪个Go版本？ | PARTIALLY_SUPPORTED_EXTRACTS | PARTIALLY_SUPPORTED | VERIFY_PROJECT_FACTS | 无 |
| 根据知识库文档，Demo要求哪个Go版本？ | SUPPORTED_EXTRACTS | SUPPORTED | ANSWER_VERIFIED_FACTS | 无 |
| 根据知识库文档，Demo是什么许可证？ | SUPPORTED_EXTRACTS | SUPPORTED | ANSWER_VERIFIED_FACTS | 无 |

用户条件来源是当前任务的开放问题；项目事实来源是本轮覆盖核验或逐项条件核验。原有任务插问不自动回答或取消，当前纯事实问答不再附带旧问题。

这轮验证了路由、状态及协议边界，没有验证真实模型的新覆盖判断准确率。短事实现可进入原有生成／核验，真实调用量及延迟需后续单独记录。

源码已接入，未启动网站后台、未迁移数据库或切换知识索引。复验：`python scripts/check_customer_evidence_gaps.py --verify`。
