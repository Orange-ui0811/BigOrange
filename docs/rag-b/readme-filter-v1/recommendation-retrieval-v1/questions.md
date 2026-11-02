# 推荐专项冻结问题集

20道主检索题（8找候选、8条件核验、4对比）及6道不足观察题。标签从完整原文确定，不以已准入内容为准。

| 编号 | 类型 | 使用方式 | 问题 | 原文标注的候选状态 |
|---|---|---|---|---|
| D01 | DISCOVER | DIRECT_DEPLOY | 想找支持与文档对话、多人使用的AI应用，先推荐有明确文档依据的项目。 | Mintplex-Labs/anything-llm: SUPPORTED |
| D02 | DISCOVER | SECONDARY_DEV | 必须能用于搭建企业内部管理后台和仪表盘，最好是低代码平台，找主要用途贴近的项目。 | appsmithorg/appsmith: SUPPORTED |
| D03 | DISCOVER | SECONDARY_DEV | 知识库入库前要静态扫描隐藏内容，要求Go编写、不依赖第三方包，并可接入CI，推荐什么项目？ | ByteBunny777/Docguard: SUPPORTED |
| D04 | DISCOVER | SECONDARY_DEV | 希望可视化编排AI工作流，还能写JavaScript和Python自定义代码，有什么项目有这些明确说明？ | n8n-io/n8n: SUPPORTED |
| D05 | DISCOVER | SECONDARY_DEV | 要修改一个React前端、Node后端的AI工具，并研究第三方节点集成，找README明确描述这些模块的项目。 | FlowiseAI/Flowise: SUPPORTED |
| D06 | DISCOVER | STUDY_SOURCE | 想读Go命令行工具的源码，重点研究goroutine worker-pool与channel收集结果，推荐有明确实现说明的项目。 | ByteBunny777/Docguard: SUPPORTED |
| D07 | DISCOVER | SECONDARY_DEV | 给文档CI增加扫描，需要能输出JSON并按告警级别让构建失败，推荐有明确接入示例的工具。 | ByteBunny777/Docguard: SUPPORTED |
| D08 | DISCOVER | DIRECT_DEPLOY | 想自托管可视化工作流，要求README给出Docker数据持久化的运行示例，请找有依据的项目。 | n8n-io/n8n: SUPPORTED |
| V01 | VERIFY | DIRECT_DEPLOY | 我只能使用Node.js 18而且不能升级，按当前README直接安装Flowise，版本条件是否冲突？ | FlowiseAI/Flowise: CONFLICT |
| V02 | VERIFY | STUDY_SOURCE | 我只能使用Go 1.21且不能升级，按当前README构建Docguard，Go版本是否符合？ | ByteBunny777/Docguard: CONFLICT |
| V03 | VERIFY | SECONDARY_DEV | 我只接受Go标准库、不允许增加第三方Go包，Docguard的依赖说明能否支持这一条件？ | ByteBunny777/Docguard: SUPPORTED |
| V04 | VERIFY | DIRECT_DEPLOY | 我的要求是保证检测出所有提示注入攻击，Docguard的README是否承诺了这种保证？ | ByteBunny777/Docguard: CONFLICT |
| V05 | VERIFY | SECONDARY_DEV | 筛选条件是README标明Apache License 2.0，Appsmith的许可证名称是否符合？只核对名称。 | appsmithorg/appsmith: SUPPORTED |
| V06 | VERIFY | SECONDARY_DEV | 我要二次开发React前端和Node后端，Flowise已有模块说明是否支持这两个技术条件？ | FlowiseAI/Flowise: SUPPORTED |
| V07 | VERIFY | DIRECT_DEPLOY | 我不接受只有云服务入口的方案，n8n当前README有没有自托管部署的明确依据？ | n8n-io/n8n: SUPPORTED |
| V08 | VERIFY | DIRECT_DEPLOY | 要求默认扫描扩展名就包含pdf且不改配置，Docguard的README默认列表是否满足？ | ByteBunny777/Docguard: CONFLICT |
| C01 | COMPARE | SECONDARY_DEV | 想做管理后台和仪表盘，比较Appsmith与Flowise README的主要定位，哪些原文直接支持这个选择？ | appsmithorg/appsmith: SUPPORTED; FlowiseAI/Flowise: UNKNOWN |
| C02 | COMPARE | STUDY_SOURCE | 想学习Go源码中的worker-pool，比较Docguard和Flowise的语言及实现说明，分别有哪些支持或缺失的依据？ | ByteBunny777/Docguard: SUPPORTED; FlowiseAI/Flowise: UNKNOWN |
| C03 | COMPARE | SECONDARY_DEV | 我想在文档入库前增加静态扫描，比较Docguard和AnythingLLM各自的主要用途，哪个有直接扫描依据？ | ByteBunny777/Docguard: SUPPORTED; Mintplex-Labs/anything-llm: UNKNOWN |
| C04 | COMPARE | SECONDARY_DEV | 需要可视化工作流和Python自定义代码，比较n8n与Flowise README对扩展方式的说明，哪些条件仍缺证据？ | n8n-io/n8n: SUPPORTED; FlowiseAI/Flowise: UNKNOWN |
| O01 | OBSERVATION | DIRECT_DEPLOY | 只有4GB内存的服务器能保证生产运行Flowise吗？README里的4096堆参数是否足够证明？ | FlowiseAI/Flowise: UNKNOWN |
| O02 | OBSERVATION | DIRECT_DEPLOY | 要求免费社区版开箱即用支持单点登录，当前Appsmith README足以核实吗？ | appsmithorg/appsmith: UNKNOWN |
| O03 | OBSERVATION | STUDY_SOURCE | 我是Java初学者，每天一小时，能保证两周吃透AnythingLLM源码吗？ | Mintplex-Labs/anything-llm: UNKNOWN |
| O04 | OBSERVATION | DIRECT_DEPLOY | 我要求任何商业场景都可免费使用，n8n README里列出的许可链接能直接证明吗？ | n8n-io/n8n: UNKNOWN |
| O05 | OBSERVATION | DIRECT_DEPLOY | Appsmith Core免费版必须内置README里Appsmith Agents介绍的能力，当前原文能证明吗？ | appsmithorg/appsmith: UNKNOWN |
| O06 | OBSERVATION | DIRECT_DEPLOY | 要求工具运行时完全不联网，Docguard所说零外部依赖足以证明这一条件吗？ | ByteBunny777/Docguard: UNKNOWN |
