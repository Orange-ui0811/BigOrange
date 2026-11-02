# 用实际接入代码逐步复现

独立工程预定目录：`D:\langgraph4j-learning`。本轮未向该目录复制平台配置或用户数据；先完成实施验收，再由你亲手逐课编写。采用 Java 21、Maven、固定 `langgraph4j-core:1.8.27`，前七课只用假数据。

| 课次 | 独立练习 | 对应平台代码 | 理解验收 |
|---|---|---|---|
| 1 | 问题→固定回答的两个节点 | LangGraphCustomerEngine 的 StateGraph/addNode/addEdge/compile/invoke | 能画出 START、两个节点和 END 的顺序，并修改回复 |
| 2 | question/intent/conditions/answer 状态更新 | CustomerGraphState、CustomerGraphSerializer、Turn.snapshot | 解释图状态与运行上下文的区别；两个请求不会互相读到状态 |
| 3 | CHAT/QUESTION/RECOMMEND 条件路由 | Turn.branch 与 addConditionalEdges | 三种输入走不同路径，未知分支明确结束 |
| 4 | 假项目库、硬条件与未知字段 | CustomerRecommendationSteps / CustomerProjects | 半年允许两个月项目；未知周期不等于已满足 |
| 5 | 假证据→事实→引用检查 | CustomerEvidenceSteps | 引用存在与支持事实不是同一个判断 |
| 6 | 节点时间线与错误注入 | CustomerExecutionTrace / CustomerUsageLedger | 根据记录定位是召回、生成还是校验失败 |
| 7 | 内存检查点与 JSON 往返 | CustomerMysqlCheckpointSaver 接口语义 | 检查点、聊天历史、正式会话各自保存什么；无自动恢复时不能宣称可回放 |
| 8 | 取消、截止时间、重复请求 | CustomerRuns / CustomerStore | 模型晚返回不能覆盖取消；重复提交只产生一条运行 |
| 9 | 假模型下新旧编排对照 | FrozenCustomerConversation、CustomerGraphTest | 对照条件/身份/调用顺序，而非只断言“代码运行没报错” |

每次只交付一课：说明目标、文件路径、可运行完整代码、运行命令与预期结果，再由你运行并解释结果。完成上一课后才进入下一课；不一次性搬入整套平台代码，不在教学项目中保存平台密钥、真实画像或知识库全文。

特别要复现本轮两个失败：诊断状态超出数据库字段导致业务降级；逐检查点额外写计时日志导致 P95 超标。它们说明“有日志”不等于“观测设计合理”，新增观测也需要真实数据库和性能验证。
