# 逐请求结果与卡片内容

字段覆盖只表示有内容；占位文字、已有分析不视为本轮事实已核实。

## P01_spring_beginner 第1轮 analysisB

终态 SUCCEEDED / None；36.18 秒；1次模型调用；估算费用 0.048276 元。

条件：`{}`



### Spring Boot 用户认证应用

https://github.com/practical-tutorials/project-based-learning | None | 引用 1 | 允许导入 None

**description**：构建一个包含注册、登录、权限控制的基础 Web 应用，学习 Spring Security 与数据持久化。

**vibeCodingReason**：需求明确，代码结构标准化，AI 可辅助生成控制器、服务、安全配置模板，降低初学门槛。

**recommendReason**：通过实战掌握 Spring Boot 最核心的用户认证与授权，为后续开发博客、后台管理系统等打下坚实基础。

**prerequisites**：Java 基础语法，环境搭建（JDK、Maven），HTTP 基本概念

### Spring Boot 集成架构示例

https://github.com/embabel/embabel-agent-examples | None | 引用 1 | 允许导入 None

**description**：探索 Spring Boot 与常见中间件（数据库、消息队列等）的集成模式，理解分层与组件通信。

**vibeCodingReason**：集成模式标准化，AI 可帮助生成依赖配置、模板类及集成测试代码，辅助理解架构。

**recommendReason**：虽然难度较高，但可作为学习目标扩展视野，了解企业级 Spring Boot 的真实集成方式。

**prerequisites**：Spring Boot 基础，数据库基础，简单了解消息队列概念

## P01_spring_beginner 第1轮 customerB

终态 SUCCEEDED / PROJECTS_WITH_LIMITATIONS；14.87 秒；2次模型调用；估算费用 0.01156328 元。

条件：`{"topic": "Spring Boot", "provenance": {"topic": {"source": "MESSAGE", "quote": "技术方向是Spring Boot"}, "goal": {"source": "MESSAGE", "quote": "目标是学习"}, "experience": {"source": "MESSAGE", "quote": "基础是刚学"}, "language": {"source": "MESSAGE", "quote": "语言是Java"}, "maxDurationDays": {"source": "MESSAGE", "quote": "完成周期是60天内"}}, "goal": "学习", "experience": "刚学", "language": "Java", "durationSemantics": {"quantity": 60, "unit": "DAY", "relation": "AT_MOST"}, "maxDurationDays": 60, "lastChangeSummary": "已记录基础（排序偏好）：刚学；已记录项目目标：学习；已记录编程语言：Java；已记录最长周期：60天；已记录项目主题：Spring Boot。其他已确认条件保持不变。", "lastCandidates": [{"id": 209, "name": "alibaba/nacos", "type": "BEGINNER_PROJECT"}, {"id": 247, "name": "jeecgboot/JeecgBoot", "type": "EXTERNAL_PROJECT"}, {"id": 75, "name": "practical-tutorials/project-based-learning", "type": "EXTERNAL_PROJECT"}]}`

已记录基础（排序偏好）：刚学；已记录项目目标：学习；已记录编程语言：Java；已记录最长周期：60天；已记录项目主题：Spring Boot。其他已确认条件保持不变。

下面是可继续考察的真实项目：已排除明确违反条件的项目，但部分资料缺失，已逐项标注。待核实候选不代表已经满足全部要求。

按当前已确认条件，当前业务项目与抓取资料中有 0 个项目通过条件检查，另有 337 个资料待核实候选（仓库去重，引用仍需核对）。
保持其余条件不变，可以考虑：
- 项目主题不限：可推荐候选共 3 个，另有 344 个待核实。
- 最长周期不限：可推荐候选共 1 个，另有 336 个待核实。
以上是本次数据快照下的试算，尚未修改条件。你想采用哪项调整？请说出要修改的条件。

### alibaba/nacos

https://github.com/alibaba/nacos | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**recommendReason**：来自学习项目资料，优先结合练手与改造分析；资料标记为入门，与当前基础更接近；仓库规模较大，不建议从重写整个系统开始；应先限定为运行示例或一个小改造任务；本轮目标：学习；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

**prerequisites**：Docker：需要；数据库：资料标记不需要；密钥：资料标记不需要。其他环境要求需核对仓库。

待核实：最长周期尚未核实，不能确认满足你的要求

### jeecgboot/JeecgBoot

https://github.com/jeecgboot/jeecgboot | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：当前基础与项目规模仍需结合具体任务判断；本轮目标：学习；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求

### practical-tutorials/project-based-learning

https://github.com/practical-tutorials/project-based-learning | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：当前基础与项目规模仍需结合具体任务判断；本轮目标：学习；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；项目主题尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求

## P01_spring_beginner 第2轮 analysisB

终态 SUCCEEDED / None；78.24 秒；1次模型调用；估算费用 0.118224 元。

条件：`{}`



### Spring Boot 用户认证实践项目

https://github.com/practical-tutorials/project-based-learning | None | 引用 1 | 允许导入 None

**description**：该候选提供 Spring Boot 用户认证实践教程，定位为通过项目学习构建带用户认证的 Spring Boot 应用，适合从可运行的小功能开始。

**vibeCodingReason**：有明确的用户认证构建目标，AI 可辅助解释认证流程、生成基础配置、排查运行报错，适合边学边问。

**recommendReason**：用户刚学 Java，目标 60 天内掌握 Spring Boot 基础。E2 提供 Spring Boot 用户认证实践教程，功能明确、有可运行目标，能覆盖控制器、配置和认证入门，产出适合作为学习示例或求职项目雏形，建议作为主线每天推进。

**prerequisites**：Java 基础语法；了解 IDE 和构建工具（如 Maven/Gradle）的基本使用；能运行简单 Java 程序。

### Spring Boot 集成架构探索项目

https://github.com/embabel/embabel-agent-examples | None | 引用 1 | 允许导入 None

**description**：该候选为 Spring Boot 集成架构示例，资料仅显示其聚焦 Spring Boot 集成架构，具体功能、依赖与可运行入口尚未核实，建议作为探索性学习项。

**vibeCodingReason**：资料模糊，AI 可帮助梳理 Spring Boot 集成架构的常见结构，但需先人工核实项目可运行性。

**recommendReason**：E3 仅展示 Spring Boot 集成架构主题，资料不足，对刚学用户来说不确定能否运行。推荐作为探索性补充，在完成 E2 主线后短时核实其仓库结构与依赖，帮助了解真实项目组织，但不应投入过多时间，避免影响基础学习。

**prerequisites**：建议已完成 Spring Boot 基础项目搭建；能通过仓库文档核实依赖和运行入口。

## P01_spring_beginner 第2轮 customerB

终态 SUCCEEDED / PROJECTS_WITH_LIMITATIONS；15.01 秒；2次模型调用；估算费用 0.009395120000000002 元。

条件：`{"goal": "学习", "provenance": {"goal": {"source": "MESSAGE", "quote": "目标是学习"}, "experience": {"source": "MESSAGE", "quote": "基础是刚学"}, "language": {"source": "MESSAGE", "quote": "语言是Java"}, "topic": {"source": "MESSAGE", "quote": "技术方向是Spring Boot"}, "maxDurationDays": {"source": "MESSAGE", "quote": "完成周期是60天内"}}, "experience": "刚学", "language": "Java", "topic": "Spring Boot", "durationSemantics": {"quantity": 60, "unit": "DAY", "relation": "AT_MOST"}, "maxDurationDays": 60, "lastChangeSummary": "已记录基础（排序偏好）：刚学；已记录项目目标：学习；已记录编程语言：Java；已记录最长周期：60天；已记录项目主题：Spring Boot。其他已确认条件保持不变。", "lastCandidates": [{"id": 209, "name": "alibaba/nacos", "type": "BEGINNER_PROJECT"}, {"id": 247, "name": "jeecgboot/JeecgBoot", "type": "EXTERNAL_PROJECT"}, {"id": 75, "name": "practical-tutorials/project-based-learning", "type": "EXTERNAL_PROJECT"}]}`

已记录基础（排序偏好）：刚学；已记录项目目标：学习；已记录编程语言：Java；已记录最长周期：60天；已记录项目主题：Spring Boot。其他已确认条件保持不变。

下面是可继续考察的真实项目：已排除明确违反条件的项目，但部分资料缺失，已逐项标注。待核实候选不代表已经满足全部要求。

按当前已确认条件，当前业务项目与抓取资料中有 0 个项目通过条件检查，另有 337 个资料待核实候选（仓库去重，引用仍需核对）。
保持其余条件不变，可以考虑：
- 项目主题不限：可推荐候选共 3 个，另有 344 个待核实。
- 最长周期不限：可推荐候选共 1 个，另有 336 个待核实。
以上是本次数据快照下的试算，尚未修改条件。你想采用哪项调整？请说出要修改的条件。

### alibaba/nacos

https://github.com/alibaba/nacos | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**recommendReason**：来自学习项目资料，优先结合练手与改造分析；资料标记为入门，与当前基础更接近；仓库规模较大，不建议从重写整个系统开始；应先限定为运行示例或一个小改造任务；本轮目标：学习；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

**prerequisites**：Docker：需要；数据库：资料标记不需要；密钥：资料标记不需要。其他环境要求需核对仓库。

待核实：最长周期尚未核实，不能确认满足你的要求

### jeecgboot/JeecgBoot

https://github.com/jeecgboot/jeecgboot | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：当前基础与项目规模仍需结合具体任务判断；本轮目标：学习；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求

### practical-tutorials/project-based-learning

https://github.com/practical-tutorials/project-based-learning | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：当前基础与项目规模仍需结合具体任务判断；本轮目标：学习；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；项目主题尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求

## P02_rag_career 第1轮 analysisB

终态 SUCCEEDED / None；12.80 秒；1次模型调用；估算费用 0.026172 元。

条件：`{}`



## P02_rag_career 第1轮 customerB

终态 SUCCEEDED / NEEDS_CLARIFICATION；5.13 秒；1次模型调用；估算费用 0.0035982799999999997 元。

条件：`{"goal": "求职", "provenance": {"goal": {"source": "MESSAGE", "quote": "目标是求职"}, "experience": {"source": "MESSAGE", "quote": "基础是有一年基础"}, "language": {"source": "MESSAGE", "quote": "语言是Java"}, "maxDurationDays": {"source": "MESSAGE", "quote": "完成周期是半年内"}}, "experience": "有一年基础", "language": "Java", "durationSemantics": {"quantity": 6, "unit": "MONTH", "relation": "AT_MOST"}, "maxDurationDays": 180, "pendingConditions": [{"quote": "请按以下本轮画像直接推荐项目：技术方向是RAG、知识库；目标是求职；基础是有一年基础；语言是Java；完成周期是半年内。只推荐 Java 项目，希望做能写进简历的知识库问答作品。条件不自动放宽；资料缺失可以展示真实探索候选，但必须标明尚未核实。请说明功能、适合原因、实践范围、环境依赖和风险。", "question": "关于“项目主题”，请确认一下具体要求；其他已确认条件会保留。", "field": "topic"}], "lastChangeSummary": "已记录基础（排序偏好）：有一年基础；已记录项目目标：求职；已记录编程语言：Java；已记录最长周期：180天。其他已确认条件保持不变。", "lastQuestion": {"text": "关于“项目主题”，请确认一下具体要求；其他已确认条件会保留。", "runId": "cfdfcd84-abcf-4ce6-95c5-3a41c5979967"}}`

已记录基础（排序偏好）：有一年基础；已记录项目目标：求职；已记录编程语言：Java；已记录最长周期：180天。其他已确认条件保持不变。

能理解的条件已经记下，还有一项需要确认，避免给你不符合要求的项目。

## P02_rag_career 第2轮 analysisB

终态 SUCCEEDED / None；12.81 秒；1次模型调用；估算费用 0.025956 元。

条件：`{}`



## P02_rag_career 第2轮 customerB

终态 SUCCEEDED / NEEDS_CLARIFICATION；8.79 秒；1次模型调用；估算费用 0.00531684 元。

条件：`{"goal": "求职", "provenance": {"goal": {"source": "MESSAGE", "quote": "求职"}, "experience": {"source": "MESSAGE", "quote": "有一年基础"}, "language": {"source": "MESSAGE", "quote": "Java"}, "maxDurationDays": {"source": "MESSAGE", "quote": "完成周期是半年内"}}, "experience": "一年基础", "language": "Java", "durationSemantics": {"quantity": 6, "unit": "MONTH", "relation": "AT_MOST"}, "maxDurationDays": 180, "pendingConditions": [{"quote": "请按以下本轮画像直接推荐项目：技术方向是RAG、知识库；目标是求职；基础是有一年基础；语言是Java；完成周期是半年内。只推荐 Java 项目，希望做能写进简历的知识库问答作品。条件不自动放宽；资料缺失可以展示真实探索候选，但必须标明尚未核实。请说明功能、适合原因、实践范围、环境依赖和风险。", "question": "关于“项目主题”，请确认一下具体要求；其他已确认条件会保留。", "field": "topic"}], "lastChangeSummary": "已记录基础（排序偏好）：一年基础；已记录项目目标：求职；已记录编程语言：Java；已记录最长周期：180天。其他已确认条件保持不变。", "lastQuestion": {"text": "关于“项目主题”，请确认一下具体要求；其他已确认条件会保留。", "runId": "89b83cbb-654f-459b-895e-157e3ba3ff9b"}}`

已记录基础（排序偏好）：一年基础；已记录项目目标：求职；已记录编程语言：Java；已记录最长周期：180天。其他已确认条件保持不变。

能理解的条件已经记下，还有一项需要确认，避免给你不符合要求的项目。

## P03_rag_python 第1轮 analysisB

终态 SUCCEEDED / None；29.75 秒；1次模型调用；估算费用 0.042252 元。

条件：`{}`



### Quivr - 开源RAG知识引擎

https://github.com/quivrhq/quivr | None | 引用 1 | 允许导入 None

**description**：基于RAG技术的第二大脑，支持与多种格式文档对话，可接入OpenAI、Anthropic、Mistral等云端模型，也支持Ollama本地模型。

**vibeCodingReason**：提供标准RAG工作流模板，配合YAML声明式配置和清晰的Python API，AI可辅助生成检索逻辑与文档处理代码。

**recommendReason**：项目直接命中RAG方向，Python实现，提供从环境配置到运行的基础RAG示例，适合在两个月内陆续完成搭建、定制与测试，为后续深入RAG优化和模型微调打下基础。

**prerequisites**：Python基础，了解API调用与环境变量管理，建议具备基本的RAG概念。

## P03_rag_python 第1轮 customerB

终态 SUCCEEDED / PROJECTS_WITH_LIMITATIONS；24.34 秒；3次模型调用；估算费用 0.01795288 元。

条件：`{"goal": "学习", "provenance": {"goal": {"source": "MESSAGE", "quote": "目标是学习"}, "experience": {"source": "MESSAGE", "quote": "基础是有基础"}, "topic": {"source": "MESSAGE", "quote": "技术方向是RAG"}, "language": {"source": "MESSAGE", "quote": "语言是Python"}, "maxDurationDays": {"source": "MESSAGE", "quote": "完成周期是两个月内"}}, "experience": "有基础", "topic": "RAG", "language": "Python", "durationSemantics": {"quantity": 2, "unit": "MONTH", "relation": "AT_MOST"}, "maxDurationDays": 60, "lastChangeSummary": "已记录基础（排序偏好）：有基础；已记录项目目标：学习；已记录编程语言：Python；已记录最长周期：60天；已记录项目主题：RAG。其他已确认条件保持不变。", "lastCandidates": [{"id": 47, "name": "kimtth/azure-openai-llm-wiki", "type": "BEGINNER_PROJECT"}, {"id": 215, "name": "headroomlabs-ai/headroom", "type": "BEGINNER_PROJECT"}, {"id": 218, "name": "TheAlgorithms/Python", "type": "EXTERNAL_PROJECT"}]}`

已记录基础（排序偏好）：有基础；已记录项目目标：学习；已记录编程语言：Python；已记录最长周期：60天；已记录项目主题：RAG。其他已确认条件保持不变。

下面是可继续考察的真实项目：已排除明确违反条件的项目，但部分资料缺失，已逐项标注。待核实候选不代表已经满足全部要求。

按当前已确认条件，当前业务项目与抓取资料中有 0 个项目通过条件检查，另有 338 个资料待核实候选（仓库去重，引用仍需核对）。
保持其余条件不变，可以考虑：
- 编程语言改为TypeScript：可推荐候选共 1 个，另有 338 个待核实。
- 项目主题不限：可推荐候选共 4 个，另有 365 个待核实。
- 最长周期不限：可推荐候选共 2 个，另有 336 个待核实。
以上是本次数据快照下的试算，尚未修改条件。你想采用哪项调整？请说出要修改的条件。

### kimtth/azure-openai-llm-wiki

https://github.com/kimtth/azure-openai-llm-wiki | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：这是一个精选的资源合集，按月更新 Azure OpenAI 服务、大语言模型及相关领域的资料，以 Markdown 文档组织。

**vibeCodingReason**：不建议作为编程作品，更适合当作学习参考资料

**recommendReason**：来自学习项目资料，优先结合练手与改造分析；本轮目标：学习；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

**prerequisites**：Docker：资料标记不需要；数据库：资料标记不需要；密钥：资料标记不需要。其他环境要求需核对仓库。

**analysisReason**：1. 无需编程，即可快速获取全面信息
2. 适合作为学习 AI 路线图
3. 能够迅速了解领域全景

**modificationPlan**：1. 将资源转为带搜索的个人知识库网站
2. 为仓库添加评论和推荐系统
3. 生成技术路线图的可视化地图

**mainRisks**：项目本身不包含可执行代码，改造后仍只是文档站，作品性极弱

待核实：最长周期尚未核实，不能确认满足你的要求

### headroomlabs-ai/headroom

https://github.com/headroomlabs-ai/headroom | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**recommendReason**：来自学习项目资料，优先结合练手与改造分析；本轮目标：学习；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

**prerequisites**：Docker：需要；数据库：资料标记不需要；密钥：需要。其他环境要求需核对仓库。

待核实：最长周期尚未核实，不能确认满足你的要求

### TheAlgorithms/Python

https://github.com/thealgorithms/python | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：本轮目标：学习；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；项目主题尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求

## P03_rag_python 第2轮 analysisB

终态 SUCCEEDED / None；46.21 秒；1次模型调用；估算费用 0.071904 元。

条件：`{}`



### Quivr 基础 RAG 工作流

https://github.com/quivrhq/quivr | None | 引用 1 | 允许导入 None

**description**：Quivr 提供基础 RAG 工作流：通过 Python 设置 OpenAI、Anthropic、Mistral API 密钥或 Ollama 本地模型，并用 YAML 定义标准 RAG 节点配置，适合学习检索增强生成流程。

**vibeCodingReason**：YAML 声明式工作流很适合由 AI 生成或调整配置，多模型 API 集成样板也可交给 AI 辅助编写，便于快速实验不同模型与 RAG 节点组合。

**recommendReason**：该项目能让你在真实仓库中实践 RAG 的最小闭环：配置模型、定义 YAML 工作流、跑通检索生成。英文文档可用中文提问辅助理解；部署依赖与版本差异需以官方 README 核实，尚未在本资料中确认。

**prerequisites**：Python 基础、环境变量配置、YAML 基本语法、任意一种支持的模型 API 或本地 Ollama 环境。

## P03_rag_python 第2轮 customerB

终态 SUCCEEDED / PROJECTS_WITH_LIMITATIONS；26.12 秒；3次模型调用；估算费用 0.01795816 元。

条件：`{"topic": "RAG", "provenance": {"topic": {"source": "MESSAGE", "quote": "技术方向是RAG"}, "goal": {"source": "MESSAGE", "quote": "目标是学习"}, "experience": {"source": "MESSAGE", "quote": "基础是有基础"}, "language": {"source": "MESSAGE", "quote": "语言是Python"}, "maxDurationDays": {"source": "MESSAGE", "quote": "完成周期是两个月内"}}, "goal": "学习", "experience": "有基础", "language": "Python", "durationSemantics": {"quantity": 2, "unit": "MONTH", "relation": "AT_MOST"}, "maxDurationDays": 60, "lastChangeSummary": "已记录基础（排序偏好）：有基础；已记录项目目标：学习；已记录编程语言：Python；已记录最长周期：60天；已记录项目主题：RAG。其他已确认条件保持不变。", "lastCandidates": [{"id": 47, "name": "kimtth/azure-openai-llm-wiki", "type": "BEGINNER_PROJECT"}, {"id": 215, "name": "headroomlabs-ai/headroom", "type": "BEGINNER_PROJECT"}, {"id": 218, "name": "TheAlgorithms/Python", "type": "EXTERNAL_PROJECT"}]}`

已记录基础（排序偏好）：有基础；已记录项目目标：学习；已记录编程语言：Python；已记录最长周期：60天；已记录项目主题：RAG。其他已确认条件保持不变。

下面是可继续考察的真实项目：已排除明确违反条件的项目，但部分资料缺失，已逐项标注。待核实候选不代表已经满足全部要求。

按当前已确认条件，当前业务项目与抓取资料中有 0 个项目通过条件检查，另有 338 个资料待核实候选（仓库去重，引用仍需核对）。
保持其余条件不变，可以考虑：
- 编程语言改为TypeScript：可推荐候选共 1 个，另有 338 个待核实。
- 项目主题不限：可推荐候选共 4 个，另有 365 个待核实。
- 最长周期不限：可推荐候选共 2 个，另有 336 个待核实。
以上是本次数据快照下的试算，尚未修改条件。你想采用哪项调整？请说出要修改的条件。

### kimtth/azure-openai-llm-wiki

https://github.com/kimtth/azure-openai-llm-wiki | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：这是一个精选的资源合集，按月更新 Azure OpenAI 服务、大语言模型及相关领域的资料，以 Markdown 文档组织。

**vibeCodingReason**：不建议作为编程作品，更适合当作学习参考资料

**recommendReason**：来自学习项目资料，优先结合练手与改造分析；本轮目标：学习；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

**prerequisites**：Docker：资料标记不需要；数据库：资料标记不需要；密钥：资料标记不需要。其他环境要求需核对仓库。

**analysisReason**：1. 无需编程，即可快速获取全面信息
2. 适合作为学习 AI 路线图
3. 能够迅速了解领域全景

**modificationPlan**：1. 将资源转为带搜索的个人知识库网站
2. 为仓库添加评论和推荐系统
3. 生成技术路线图的可视化地图

**mainRisks**：项目本身不包含可执行代码，改造后仍只是文档站，作品性极弱

待核实：最长周期尚未核实，不能确认满足你的要求

### headroomlabs-ai/headroom

https://github.com/headroomlabs-ai/headroom | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**recommendReason**：来自学习项目资料，优先结合练手与改造分析；本轮目标：学习；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

**prerequisites**：Docker：需要；数据库：资料标记不需要；密钥：需要。其他环境要求需核对仓库。

待核实：最长周期尚未核实，不能确认满足你的要求

### TheAlgorithms/Python

https://github.com/thealgorithms/python | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：本轮目标：学习；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；项目主题尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求

## P04_agent_multi 第1轮 analysisB

终态 SUCCEEDED / None；40.04 秒；1次模型调用；估算费用 0.058488 元。

条件：`{}`



### Agent团队协作平台

https://github.com/777genius/agent-teams-ai | None | 引用 1 | 允许导入 None

**description**：一个多智能体协作平台，可指挥多个AI智能体自主执行任务、相互通信与审查，支持200+模型和75+LLM提供商。

**vibeCodingReason**：Agent间通信协议、模型适配层、任务分发逻辑均可由AI辅助生成，适合Vibe Coding方式快速原型迭代。

**recommendReason**：直接匹配Agent学习目标，可深入理解多智能体协作原理。将其改造为服务小团队的内部工具（如接入私有模型、定制审查流），既能积累Agent实战经验，又产出可复用产品。不过需注意仓库语言未明确，可能需前期调研是否兼容TypeScript。

**prerequisites**：TypeScript基础、Node.js、HTTP/WebSocket通信、提示工程概念

### AI辅助编程与团队工作流工具

https://github.com/chaitin/monkeycode | None | 引用 1 | 允许导入 None

**description**：结合AI辅助编程与团队协作，帮助开发者理解提示工程如何融入日常工作流，功能模块独立，易上手。

**vibeCodingReason**：项目本身就涉及AI辅助编程，可自然使用AI生成功能扩展、测试用例和文档，形成闭环学习。

**recommendReason**：适合作为Agent方向的切入口：通过改造提示流、增加Agent决策模块，可演进为团队Agent助手。同时直接服务小团队开发提效，实现‘学习+实用’双目标。风险较低，依赖环境明确。

**prerequisites**：TypeScript、Node.js、Git、基础命令行工具

## P04_agent_multi 第1轮 customerB

终态 FAILED / FAILED；17.58 秒；2次模型调用；估算费用 0.01263488 元。

条件：`{}`

我还没能可靠地整理这句话中的要求，因此没有擅自更改条件。你可以继续解释最重要的一项，我会接着帮你；已有会话仍然保留。

## P04_agent_multi 第2轮 analysisB

终态 SUCCEEDED / None；52.41 秒；1次模型调用；估算费用 0.074784 元。

条件：`{}`



### Pireel 视频编辑器

https://github.com/pireel/pireel | None | 引用 1 | 允许导入 None

**description**：一个基于React和Vite的在线视频编辑器，内置AI代理可一键生成视频，支持自定义主题和导出高清内容。

**vibeCodingReason**：前端组件化开发天然适合AI辅助编码；AI代理生成视频的功能可通过提示工程快速扩展，实验成本低。

**recommendReason**：项目紧扣Agent方向，通过集成AI视频生成学习智能体调用与自动化流程；可改造为团队内容创作工具，满足接单需求，三个月内能完成原型并封装。

**prerequisites**：React、TypeScript、Vite

### MonkeyCode AI辅助编程

https://github.com/chaitin/monkeycode | None | 引用 1 | 允许导入 None

**description**：面向团队的AI辅助编程平台，融合提示工程与开发者工作流，模块独立，便于扩展和定制。

**vibeCodingReason**：涉及LLM调用与提示设计，适合通过AI辅助方式迭代功能，降低团队工具开发门槛。

**recommendReason**：直接命中“服务小团队的工具”目标，可学习智能体在编程辅助中的应用；改造为内部工具后具备商业化潜力，适合学习与接单实践。

**prerequisites**：TypeScript、Node.js、基础LLM调用

## P04_agent_multi 第2轮 customerB

终态 SUCCEEDED / PROJECTS_WITH_LIMITATIONS；16.43 秒；2次模型调用；估算费用 0.012056840000000001 元。

条件：`{"topic": "Agent", "provenance": {"topic": {"source": "MESSAGE", "quote": "技术方向是Agent、智能体"}, "goal": {"source": "MESSAGE", "quote": "目标是学习；赚钱/接单/创业"}, "experience": {"source": "MESSAGE", "quote": "基础是有两年基础"}, "language": {"source": "MESSAGE", "quote": "语言是TypeScript"}, "maxDurationDays": {"source": "MESSAGE", "quote": "完成周期是三个月内"}}, "goal": ["学习", "赚钱/接单/创业"], "experience": "有两年基础", "language": "TypeScript", "durationSemantics": {"quantity": 3, "unit": "MONTH", "relation": "AT_MOST"}, "maxDurationDays": 90, "lastChangeSummary": "已记录基础（排序偏好）：有两年基础；已记录项目目标：学习、赚钱/接单/创业；已记录编程语言：TypeScript；已记录最长周期：90天；已记录项目主题：Agent。其他已确认条件保持不变。", "lastCandidates": [{"id": 53, "name": "olo-dot-io/Uni-CLI", "type": "BEGINNER_PROJECT"}, {"id": 212, "name": "deer-flow/llm-space", "type": "CAREER_PROJECT"}, {"id": 82, "name": "makecindy/cindy", "type": "CAREER_PROJECT"}]}`

已记录基础（排序偏好）：有两年基础；已记录项目目标：学习、赚钱/接单/创业；已记录编程语言：TypeScript；已记录最长周期：90天；已记录项目主题：Agent。其他已确认条件保持不变。

找到以下满足已核实条件的真实项目。周期是资料中的估算，实际完成时间还取决于实现范围和投入。

### olo-dot-io/Uni-CLI

https://github.com/olo-dot-io/uni-cli | CONFIRMED | 引用 0 | 允许导入 False

**description**：一个命令同时操作 API、浏览器、桌面、本地工具和 MCP 等多种界面，实现搜索、运行和检查软件。

**recommendReason**：来自学习项目资料，优先结合练手与改造分析；本轮目标：学习、赚钱/接单/创业；自定义目标的实际契合度需结合下面的说明与证据判断；已通过当前已知字段的条件检查，周期仅为资料估算。补充资料核对暂未完成，部署、功能等细节仍需确认。

**prerequisites**：Docker：资料标记不需要；数据库：资料标记不需要；密钥：资料标记不需要。其他环境要求需核对仓库。

**analysisReason**：TypeScript 项目，功能模块化；涵盖多种接口的统一调用，适合学习命令行工具开发、进程管理和协议适配。

### deer-flow/llm-space

https://github.com/deer-flow/llm-space | CONFIRMED | 引用 0 | 允许导入 False

**description**：llm-space：用于快速原型 Agent 想法的桌面应用，支持本地优先的调试与评估。

**recommendReason**：本轮目标：学习、赚钱/接单/创业；自定义目标的实际契合度需结合下面的说明与证据判断；已通过当前已知字段的条件检查，周期仅为资料估算。补充资料核对暂未完成，部署、功能等细节仍需确认。

**prerequisites**：Docker：资料标记不需要；数据库：资料标记不需要；密钥：需要。其他环境要求需核对仓库。

**analysisReason**：体现 Agent 开发与原型设计能力，适合 AI 应用与 Agent 或全栈后端岗位。

**modificationPlan**：集成主流 LLM 提供商的 API（如 OpenAI）作为后端；添加多用户协作功能并实现数据库持久化；制作可演示的复杂 Agent 工作流示例。

**proofPoints**：测试充分，持续集成，自动化发布，大型项目规模，目前仅有 3 个开放 issue。

**mainRisks**：项目缺少数据库和 Docker 支持，后端能力展示不足；需要一定 React 前端基础。

### makecindy/cindy

https://github.com/makecindy/cindy | CONFIRMED | 引用 0 | 允许导入 False

**description**：Cindy 是一个开箱即用的开源AI Agent（“想到，就能做到”）。基于TypeScript构建，提供架构文档和CI/CD，无数据库和Docker支持，采用Apache-2.0许可证。

**recommendReason**：本轮目标：学习、赚钱/接单/创业；自定义目标的实际契合度需结合下面的说明与证据判断；已通过当前已知字段的条件检查，周期仅为资料估算。补充资料核对暂未完成，部署、功能等细节仍需确认。

**prerequisites**：Docker：资料标记不需要；数据库：资料标记不需要；密钥：需要。其他环境要求需核对仓库。

**analysisReason**：最适合AI应用与Agent方向，同时适用于AI产品运营和全栈工程。完成本项目能证明AI Agent产品化、架构设计和工程化能力，面试时可以深入探讨Agent自主性与任务拆解。

**modificationPlan**：为Agent添加数据库持久化（当前无），并引入Docker化部署；修复几个高优先级Issue（443个开放中），例如增强多模型适配；实现一个中文对话界面，让Agent具备本土化理解能力；编写使用案例教程，展示差异化应用。

**proofPoints**：- 掌握AI Agent的工程实现，包括工具调用、记忆管理
- 完善的测试、CI与自动化发布流水线
- 架构文档帮助理解设计决策
- 快速启动脚本降低上手成本
- Apache-2.0许可支持商业与学习使用

**mainRisks**：大型项目（LARGE），代码复杂；缺少数据库和Docker可能限制实际使用场景；443个开放Issue表示项目活跃但维护压力大，需谨慎选择贡献点；前沿AI技术迭代快，可能过时。

## P05_vue_no_docker 第1轮 analysisB

终态 SUCCEEDED / None；114.29 秒；1次模型调用；估算费用 0.192888 元。

条件：`{}`



### Vue 表单配置生成器

https://github.com/justarchinet/asf-webconfiggenerator | None | 引用 1 | 允许导入 None

**description**：基于 Vue 的网页配置生成工具，以表单驱动方式生成配置数据，适合练习 Vue 组件开发、表单绑定与数据配置逻辑，门槛较低，可作前端演示作品。

**vibeCodingReason**：表单绑定、组件拆分和配置数据逻辑边界清晰，适合用 AI 辅助分步实现与调试。

**recommendReason**：该项目为纯前端 JavaScript 与 Vue 实现，能在一个月内帮助你通过真实项目练习组件拆分、表单双向绑定、配置数据生成和页面展示，无需 Docker 即可本地运行，完成后可作为个人作品演示。

**prerequisites**：HTML/CSS/JavaScript 基础；Vue 模板、指令与组件基础。

### Canvas UI 视觉组件（探索候选）

https://github.com/davidhdev/canvas-ui | None | 引用 1 | 允许导入 None

**description**：候选资料描述为基于 shadcn CLI 添加的前端视觉组件，包裹内容即可获得视觉效果，可复制源码学习 WebGL/Canvas 原理；但具体技术栈与 Vue 适配尚未核实。

**vibeCodingReason**：组件封装与视觉反馈直观，适合借助 AI 理解源码结构、小步修改并快速看到效果。

**recommendReason**：作为探索候选适合做个人主页或作品集包装，视觉反馈直接，能提升学习兴趣；但资料显示可能涉及 React/Vue 且技术栈未核实，需要先确认项目是否纯 Vue/JavaScript，再决定是否投入练习。

**prerequisites**：Vue 基础与组件使用；若深入源码需了解 Canvas/WebGL 基础，并需先核实项目对 Vue 的支持。

## P05_vue_no_docker 第1轮 customerB

终态 SUCCEEDED / PROJECTS_WITH_LIMITATIONS；15.47 秒；2次模型调用；估算费用 0.010471280000000001 元。

条件：`{"goal": "学习", "provenance": {"goal": {"source": "MESSAGE", "quote": "目标是学习"}, "topic": {"source": "MESSAGE", "quote": "技术方向是Vue"}, "experience": {"source": "MESSAGE", "quote": "基础是刚学"}, "language": {"source": "MESSAGE", "quote": "语言是JavaScript"}, "maxDurationDays": {"source": "MESSAGE", "quote": "完成周期是一个月内"}, "allowDocker": {"source": "MESSAGE", "quote": "不使用 Docker"}}, "topic": "Vue", "experience": "刚学", "language": "JavaScript", "durationSemantics": {"quantity": 1, "unit": "MONTH", "relation": "AT_MOST"}, "maxDurationDays": 30, "allowDocker": false, "lastChangeSummary": "已记录Docker要求：不使用；已记录基础（排序偏好）：刚学；已记录项目目标：学习；已记录编程语言：JavaScript；已记录最长周期：30天；已记录项目主题：Vue。其他已确认条件保持不变。", "lastCandidates": [{"id": 36, "name": "JustArchiNET/ASF-WebConfigGenerator", "type": "BEGINNER_PROJECT"}, {"id": 37, "name": "VeteranBoLuo/light-note", "type": "BEGINNER_PROJECT"}, {"id": 400, "name": "visgl/deck.gl", "type": "EXTERNAL_PROJECT"}]}`

已记录Docker要求：不使用；已记录基础（排序偏好）：刚学；已记录项目目标：学习；已记录编程语言：JavaScript；已记录最长周期：30天；已记录项目主题：Vue。其他已确认条件保持不变。

下面是可继续考察的真实项目：已排除明确违反条件的项目，但部分资料缺失，已逐项标注。待核实候选不代表已经满足全部要求。

### JustArchiNET/ASF-WebConfigGenerator

https://github.com/justarchinet/asf-webconfiggenerator | CONFIRMED | 引用 0 | 允许导入 False

**description**：一款基于Vue.js的ArchiSteamFarm配置文件在线生成器。

**recommendReason**：来自学习项目资料，优先结合练手与改造分析；资料标记为入门，与当前基础更接近；本轮目标：学习；自定义目标的实际契合度需结合下面的说明与证据判断；已通过当前已知字段的条件检查，周期仅为资料估算。补充资料核对暂未完成，部署、功能等细节仍需确认。

**prerequisites**：Docker：资料标记不需要；数据库：资料标记不需要；密钥：资料标记不需要。其他环境要求需核对仓库。

**analysisReason**：以表单驱动的配置生成工具，可练习Vue组件开发和数据配置逻辑，门槛较低。

### VeteranBoLuo/light-note

https://github.com/veteranboluo/light-note | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：一个集书签管理、笔记编辑、文件存储于一体的在线工具，内置AI辅助功能，数据可跨设备同步。

**vibeCodingReason**：非常推荐给愿意花半天时间搞定数据库的新手，改造成个人作品集或实用工具的效果极佳。

**recommendReason**：来自学习项目资料，优先结合练手与改造分析；资料标记为入门，与当前基础更接近；本轮目标：学习；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

**prerequisites**：Docker：资料标记不需要；数据库：需要；密钥：资料标记不需要。其他环境要求需核对仓库。

**analysisReason**：• 本项目是一个完整的全栈应用，需要Node.js和数据库（如MySQL），但代码结构清晰。
• 新手通过部署一次，就能理解前后端配合和数据库操作的实际流程。
• 内置AI助手已经打通，可以直接体验AI驱动的内容搜索和摘要，很有成就感。
• 界面功能完善，适合拿来即用，边用边改，学习价值高。

**modificationPlan**：• 改造前端配色和布局，做成特定主题的“程序员专用收藏夹”。
• 为笔记增加手写画图功能，并将其同步到云端。
• 扩展AI助手，让它能自动为每篇笔记生成配图或语音朗读。

**mainRisks**：初始化数据库可能卡住新手，需要提前掌握简单的数据库创建和连接配置。

待核实：最长周期尚未核实，不能确认满足你的要求

### visgl/deck.gl

https://github.com/visgl/deck.gl | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：当前基础与项目规模仍需结合具体任务判断；本轮目标：学习；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；项目主题尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求；Docker要求尚未核实，不能确认满足你的要求

## P05_vue_no_docker 第2轮 analysisB

终态 SUCCEEDED / None；26.63 秒；1次模型调用；估算费用 0.039036 元。

条件：`{}`



### ASF-WebConfigGenerator

https://github.com/justarchinet/asf-webconfiggenerator | None | 引用 1 | 允许导入 None

**description**：一个表单驱动的配置生成工具，帮助用户通过Web界面生成Archisteamfarm的配置文件，专注Vue组件开发和数据配置逻辑。

**vibeCodingReason**：项目结构简单，UI组件化程度高，AI可辅助生成表单组件、校验逻辑和配置模板，降低开发难度。

**recommendReason**：一个月内可完成，通过构建实际可用的配置生成工具，深入理解Vue组件化、表单处理和状态管理，成果可直接演示，匹配刚学阶段的实践目标。

**prerequisites**：HTML、CSS、JavaScript基础，Vue入门知识（如组件、数据绑定）

## P05_vue_no_docker 第2轮 customerB

终态 SUCCEEDED / PROJECTS_WITH_LIMITATIONS；17.07 秒；2次模型调用；估算费用 0.01108408 元。

条件：`{"topic": "Vue", "provenance": {"topic": {"source": "MESSAGE", "quote": "技术方向是Vue"}, "goal": {"source": "MESSAGE", "quote": "目标是学习"}, "experience": {"source": "MESSAGE", "quote": "基础是刚学"}, "language": {"source": "MESSAGE", "quote": "语言是JavaScript"}, "maxDurationDays": {"source": "MESSAGE", "quote": "完成周期是一个月内"}, "allowDocker": {"source": "MESSAGE", "quote": "不使用 Docker"}}, "goal": "学习", "experience": "刚学", "language": "JavaScript", "durationSemantics": {"quantity": 1, "unit": "MONTH", "relation": "AT_MOST"}, "maxDurationDays": 30, "allowDocker": false, "lastChangeSummary": "已记录Docker要求：不使用；已记录基础（排序偏好）：刚学；已记录项目目标：学习；已记录编程语言：JavaScript；已记录最长周期：30天；已记录项目主题：Vue。其他已确认条件保持不变。", "lastCandidates": [{"id": 36, "name": "JustArchiNET/ASF-WebConfigGenerator", "type": "BEGINNER_PROJECT"}, {"id": 37, "name": "VeteranBoLuo/light-note", "type": "BEGINNER_PROJECT"}, {"id": 400, "name": "visgl/deck.gl", "type": "EXTERNAL_PROJECT"}]}`

已记录Docker要求：不使用；已记录基础（排序偏好）：刚学；已记录项目目标：学习；已记录编程语言：JavaScript；已记录最长周期：30天；已记录项目主题：Vue。其他已确认条件保持不变。

下面是可继续考察的真实项目：已排除明确违反条件的项目，但部分资料缺失，已逐项标注。待核实候选不代表已经满足全部要求。

### JustArchiNET/ASF-WebConfigGenerator

https://github.com/justarchinet/asf-webconfiggenerator | CONFIRMED | 引用 0 | 允许导入 False

**description**：一款基于Vue.js的ArchiSteamFarm配置文件在线生成器。

**recommendReason**：来自学习项目资料，优先结合练手与改造分析；资料标记为入门，与当前基础更接近；本轮目标：学习；自定义目标的实际契合度需结合下面的说明与证据判断；已通过当前已知字段的条件检查，周期仅为资料估算。补充资料核对暂未完成，部署、功能等细节仍需确认。

**prerequisites**：Docker：资料标记不需要；数据库：资料标记不需要；密钥：资料标记不需要。其他环境要求需核对仓库。

**analysisReason**：以表单驱动的配置生成工具，可练习Vue组件开发和数据配置逻辑，门槛较低。

### VeteranBoLuo/light-note

https://github.com/veteranboluo/light-note | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：一个集书签管理、笔记编辑、文件存储于一体的在线工具，内置AI辅助功能，数据可跨设备同步。

**vibeCodingReason**：非常推荐给愿意花半天时间搞定数据库的新手，改造成个人作品集或实用工具的效果极佳。

**recommendReason**：来自学习项目资料，优先结合练手与改造分析；资料标记为入门，与当前基础更接近；本轮目标：学习；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

**prerequisites**：Docker：资料标记不需要；数据库：需要；密钥：资料标记不需要。其他环境要求需核对仓库。

**analysisReason**：• 本项目是一个完整的全栈应用，需要Node.js和数据库（如MySQL），但代码结构清晰。
• 新手通过部署一次，就能理解前后端配合和数据库操作的实际流程。
• 内置AI助手已经打通，可以直接体验AI驱动的内容搜索和摘要，很有成就感。
• 界面功能完善，适合拿来即用，边用边改，学习价值高。

**modificationPlan**：• 改造前端配色和布局，做成特定主题的“程序员专用收藏夹”。
• 为笔记增加手写画图功能，并将其同步到云端。
• 扩展AI助手，让它能自动为每篇笔记生成配图或语音朗读。

**mainRisks**：初始化数据库可能卡住新手，需要提前掌握简单的数据库创建和连接配置。

待核实：最长周期尚未核实，不能确认满足你的要求

### visgl/deck.gl

https://github.com/visgl/deck.gl | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：当前基础与项目规模仍需结合具体任务判断；本轮目标：学习；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；项目主题尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求；Docker要求尚未核实，不能确认满足你的要求

## P06_data_career 第1轮 analysisB

终态 SUCCEEDED / None；46.30 秒；1次模型调用；估算费用 0.04944 元。

条件：`{}`



### Apache Doris 实时数据分析实践

https://github.com/apache/doris | None | 引用 1 | 允许导入 None

**description**：围绕 Apache Doris 构建实时数据查询与可视化项目，使用 Python 进行数据导入、处理与看板开发，展现大数据分析全流程能力

**vibeCodingReason**：AI 可辅助生成 Doris 建表语句、Python 数据加载脚本和可视化代码，降低上手门槛

**recommendReason**：项目直接对标数据分析岗位中的‘实时数仓与可视化’需求，通过 Python 对接 Doris 完成从数据接入到看板展示，完整体现数据处理与可视化能力，是简历中的高价值实践

**prerequisites**：Python 基础、SQL、数据仓库基本概念

## P06_data_career 第1轮 customerB

终态 SUCCEEDED / PROJECTS_WITH_LIMITATIONS；15.47 秒；2次模型调用；估算费用 0.01122628 元。

条件：`{"topic": "数据分析", "provenance": {"topic": {"source": "MESSAGE", "quote": "技术方向是数据分析"}, "goal": {"source": "MESSAGE", "quote": "目标是求职"}, "experience": {"source": "MESSAGE", "quote": "基础是有基础"}, "language": {"source": "MESSAGE", "quote": "语言是Python"}, "maxDurationDays": {"source": "MESSAGE", "quote": "完成周期是两个月内"}}, "goal": "求职", "experience": "有基础", "language": "Python", "durationSemantics": {"quantity": 2, "unit": "MONTH", "relation": "AT_MOST"}, "maxDurationDays": 60, "lastChangeSummary": "已记录基础（排序偏好）：有基础；已记录项目目标：求职；已记录编程语言：Python；已记录最长周期：60天；已记录项目主题：数据分析。其他已确认条件保持不变。", "lastCandidates": [{"id": 218, "name": "TheAlgorithms/Python", "type": "EXTERNAL_PROJECT"}, {"id": 75, "name": "practical-tutorials/project-based-learning", "type": "EXTERNAL_PROJECT"}, {"id": 194, "name": "1919884213/cjson-toolkit", "type": "EXTERNAL_PROJECT"}]}`

已记录基础（排序偏好）：有基础；已记录项目目标：求职；已记录编程语言：Python；已记录最长周期：60天；已记录项目主题：数据分析。其他已确认条件保持不变。

下面是可继续考察的真实项目：已排除明确违反条件的项目，但部分资料缺失，已逐项标注。待核实候选不代表已经满足全部要求。

按当前已确认条件，当前业务项目与抓取资料中有 0 个项目通过条件检查，另有 336 个资料待核实候选（仓库去重，引用仍需核对）。
保持其余条件不变，可以考虑：
- 项目主题不限：可推荐候选共 4 个，另有 365 个待核实。
以上是本次数据快照下的试算，尚未修改条件。你想采用哪项调整？请说出要修改的条件。

### TheAlgorithms/Python

https://github.com/thealgorithms/python | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：本轮目标：求职；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；项目主题尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求

### practical-tutorials/project-based-learning

https://github.com/practical-tutorials/project-based-learning | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：本轮目标：求职；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；项目主题尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求

### 1919884213/cjson-toolkit

https://github.com/1919884213/cjson-toolkit | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：本轮目标：求职；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；项目主题尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求

## P06_data_career 第2轮 analysisB

终态 SUCCEEDED / None；58.71 秒；1次模型调用；估算费用 0.089652 元。

条件：`{}`



## P06_data_career 第2轮 customerB

终态 SUCCEEDED / PROJECTS_WITH_LIMITATIONS；16.06 秒；2次模型调用；估算费用 0.009650120000000002 元。

条件：`{"topic": "数据分析", "provenance": {"topic": {"source": "MESSAGE", "quote": "技术方向是数据分析"}, "goal": {"source": "MESSAGE", "quote": "目标是求职"}, "experience": {"source": "MESSAGE", "quote": "基础是有基础"}, "language": {"source": "MESSAGE", "quote": "语言是Python"}, "maxDurationDays": {"source": "MESSAGE", "quote": "完成周期是两个月内"}}, "goal": "求职", "experience": "有基础", "language": "Python", "durationSemantics": {"quantity": 2, "unit": "MONTH", "relation": "AT_MOST"}, "maxDurationDays": 60, "lastChangeSummary": "已记录基础（排序偏好）：有基础；已记录项目目标：求职；已记录编程语言：Python；已记录最长周期：60天；已记录项目主题：数据分析。其他已确认条件保持不变。", "lastCandidates": [{"id": 218, "name": "TheAlgorithms/Python", "type": "EXTERNAL_PROJECT"}, {"id": 75, "name": "practical-tutorials/project-based-learning", "type": "EXTERNAL_PROJECT"}, {"id": 194, "name": "1919884213/cjson-toolkit", "type": "EXTERNAL_PROJECT"}]}`

已记录基础（排序偏好）：有基础；已记录项目目标：求职；已记录编程语言：Python；已记录最长周期：60天；已记录项目主题：数据分析。其他已确认条件保持不变。

下面是可继续考察的真实项目：已排除明确违反条件的项目，但部分资料缺失，已逐项标注。待核实候选不代表已经满足全部要求。

按当前已确认条件，当前业务项目与抓取资料中有 0 个项目通过条件检查，另有 336 个资料待核实候选（仓库去重，引用仍需核对）。
保持其余条件不变，可以考虑：
- 项目主题不限：可推荐候选共 4 个，另有 365 个待核实。
以上是本次数据快照下的试算，尚未修改条件。你想采用哪项调整？请说出要修改的条件。

### TheAlgorithms/Python

https://github.com/thealgorithms/python | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：本轮目标：求职；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；项目主题尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求

### practical-tutorials/project-based-learning

https://github.com/practical-tutorials/project-based-learning | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：本轮目标：求职；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；项目主题尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求

### 1919884213/cjson-toolkit

https://github.com/1919884213/cjson-toolkit | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：本轮目标：求职；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；项目主题尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求

## P07_custom_inventory 第1轮 analysisB

终态 SUCCEEDED / None；11.13 秒；1次模型调用；估算费用 0.017316 元。

条件：`{}`



## P07_custom_inventory 第1轮 customerB

终态 FAILED / FAILED；18.60 秒；2次模型调用；估算费用 0.012750319999999999 元。

条件：`{}`

我还没能可靠地整理这句话中的要求，因此没有擅自更改条件。你可以继续解释最重要的一项，我会接着帮你；已有会话仍然保留。

## P07_custom_inventory 第2轮 analysisB

终态 SUCCEEDED / None；11.59 秒；1次模型调用；估算费用 0.017436 元。

条件：`{}`



## P07_custom_inventory 第2轮 customerB

终态 SUCCEEDED / PROJECTS_WITH_LIMITATIONS；18.53 秒；2次模型调用；估算费用 0.01376484 元。

条件：`{"topic": "库存管理", "provenance": {"topic": {"source": "MESSAGE", "quote": "技术方向是库存管理"}, "goal": {"source": "MESSAGE", "quote": "目标是给家里的小店做库存管理"}, "experience": {"source": "MESSAGE", "quote": "基础是刚学"}, "language": {"source": "MESSAGE", "quote": "语言是Java"}, "maxDurationDays": {"source": "MESSAGE", "quote": "四个月内"}, "allowDocker": {"source": "MESSAGE", "quote": "不使用 Docker"}}, "goal": "给家里的小店做库存管理", "experience": "刚学", "language": "Java", "durationSemantics": {"quantity": 4, "unit": "MONTH", "relation": "AT_MOST"}, "maxDurationDays": 120, "allowDocker": false, "lastChangeSummary": "已记录Docker要求：不使用；已记录基础（排序偏好）：刚学；已记录项目目标：给家里的小店做库存管理；已记录编程语言：Java；已记录最长周期：120天；已记录项目主题：库存管理。其他已确认条件保持不变。", "lastCandidates": [{"id": 69, "name": "codecrafters-io/build-your-own-x", "type": "EXTERNAL_PROJECT"}, {"id": 329, "name": "ToolJet/ToolJet", "type": "EXTERNAL_PROJECT"}, {"id": 75, "name": "practical-tutorials/project-based-learning", "type": "EXTERNAL_PROJECT"}]}`

已记录Docker要求：不使用；已记录基础（排序偏好）：刚学；已记录项目目标：给家里的小店做库存管理；已记录编程语言：Java；已记录最长周期：120天；已记录项目主题：库存管理。其他已确认条件保持不变。

下面是可继续考察的真实项目：已排除明确违反条件的项目，但部分资料缺失，已逐项标注。待核实候选不代表已经满足全部要求。

按当前已确认条件，当前业务项目与抓取资料中有 0 个项目通过条件检查，另有 336 个资料待核实候选（仓库去重，引用仍需核对）。
保持其余条件不变，可以考虑：
- 项目主题不限：可推荐候选共 1 个，另有 340 个待核实。
以上是本次数据快照下的试算，尚未修改条件。你想采用哪项调整？请说出要修改的条件。

### codecrafters-io/build-your-own-x

https://github.com/codecrafters-io/build-your-own-x | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：当前基础与项目规模仍需结合具体任务判断；本轮目标：给家里的小店做库存管理；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；项目主题尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求；Docker要求尚未核实，不能确认满足你的要求

### ToolJet/ToolJet

https://github.com/tooljet/tooljet | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：当前基础与项目规模仍需结合具体任务判断；本轮目标：给家里的小店做库存管理；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；项目主题尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求；Docker要求尚未核实，不能确认满足你的要求

### practical-tutorials/project-based-learning

https://github.com/practical-tutorials/project-based-learning | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：当前基础与项目规模仍需结合具体任务判断；本轮目标：给家里的小店做库存管理；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；项目主题尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求；Docker要求尚未核实，不能确认满足你的要求

## P08_unavailable 第1轮 analysisB

终态 SUCCEEDED / None；21.88 秒；1次模型调用；估算费用 0.031992 元。

条件：`{}`



## P08_unavailable 第1轮 customerB

终态 FAILED / FAILED；18.68 秒；2次模型调用；估算费用 0.012654879999999999 元。

条件：`{}`

我还没能可靠地整理这句话中的要求，因此没有擅自更改条件。你可以继续解释最重要的一项，我会接着帮你；已有会话仍然保留。

## P08_unavailable 第2轮 analysisB

终态 SUCCEEDED / None；19.26 秒；1次模型调用；估算费用 0.027504 元。

条件：`{}`



## P08_unavailable 第2轮 customerB

终态 SUCCEEDED / PROJECTS_WITH_LIMITATIONS；17.60 秒；2次模型调用；估算费用 0.01266184 元。

条件：`{"goal": "探索技术兴趣", "provenance": {"goal": {"source": "MESSAGE", "quote": "目标是探索技术兴趣"}, "experience": {"source": "MESSAGE", "quote": "基础是刚学"}, "topic": {"source": "MESSAGE", "quote": "技术方向是量子纠错芯片流片"}, "language": {"source": "MESSAGE", "quote": "语言是Java"}, "maxDurationDays": {"source": "MESSAGE", "quote": "完成周期是两天内"}}, "experience": "刚学", "topic": "量子纠错芯片流片", "language": "Java", "durationSemantics": {"quantity": 2, "unit": "DAY", "relation": "AT_MOST"}, "maxDurationDays": 2, "lastChangeSummary": "已记录基础（排序偏好）：刚学；已记录项目目标：探索技术兴趣；已记录编程语言：Java；已记录最长周期：2天；已记录项目主题：量子纠错芯片流片。其他已确认条件保持不变。", "lastCandidates": [{"id": 75, "name": "practical-tutorials/project-based-learning", "type": "EXTERNAL_PROJECT"}, {"id": 194, "name": "1919884213/cjson-toolkit", "type": "EXTERNAL_PROJECT"}, {"id": 545, "name": "1e3pm/knowledge-chatbot", "type": "EXTERNAL_PROJECT"}]}`

已记录基础（排序偏好）：刚学；已记录项目目标：探索技术兴趣；已记录编程语言：Java；已记录最长周期：2天；已记录项目主题：量子纠错芯片流片。其他已确认条件保持不变。

下面是可继续考察的真实项目：已排除明确违反条件的项目，但部分资料缺失，已逐项标注。待核实候选不代表已经满足全部要求。

按当前已确认条件，当前业务项目与抓取资料中有 0 个项目通过条件检查，另有 336 个资料待核实候选（仓库去重，引用仍需核对）。
保持其余条件不变，可以考虑：
- 项目主题不限：可推荐候选共 0 个，另有 347 个待核实。
以上是本次数据快照下的试算，尚未修改条件。你想采用哪项调整？请说出要修改的条件。

### practical-tutorials/project-based-learning

https://github.com/practical-tutorials/project-based-learning | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：当前基础与项目规模仍需结合具体任务判断；本轮目标：探索技术兴趣；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；项目主题尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求

### 1919884213/cjson-toolkit

https://github.com/1919884213/cjson-toolkit | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：当前基础与项目规模仍需结合具体任务判断；本轮目标：探索技术兴趣；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；项目主题尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求

### 1e3pm/knowledge-chatbot

https://github.com/1e3pm/knowledge-chatbot | NEEDS_VERIFICATION | 引用 0 | 允许导入 False

**description**：已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。

**recommendReason**：当前基础与项目规模仍需结合具体任务判断；本轮目标：探索技术兴趣；自定义目标的实际契合度需结合下面的说明与证据判断；明确冲突已排除，仍有条件待核实。补充资料核对暂未完成，部署、功能等细节仍需确认。

待核实：编程语言尚未核实，不能确认满足你的要求；项目主题尚未核实，不能确认满足你的要求；最长周期尚未核实，不能确认满足你的要求
