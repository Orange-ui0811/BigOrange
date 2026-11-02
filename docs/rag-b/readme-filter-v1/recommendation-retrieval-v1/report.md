# 推荐专项：A/B/C纯向量检索结果

20道主检索题、6道不足观察题；原文、分类及三组分块完全沿用前轮。仅新增查询，三组共用查询向量。无关键词、名称加分、生成或判断模型。

| 组别 | 找到候选 /8 | 找候选证据完整 /8 | 条件核验证据完整 /8 | 比较证据完整 /4 | 主集完整 /20 | 加必要限制 /20 |
|---|---:|---:|---:|---:|---:|---:|
| A | 8/8 | 6/8 | 7/8 | 2/4 | 15/20 | 12/20 |
| B | 6/8 | 5/8 | 6/8 | 1/4 | 12/20 | 9/20 |
| C | 6/8 | 5/8 | 6/8 | 0/4 | 11/20 | 9/20 |

“证据完整”只说明找齐了标注事实；不能解释为模型已正确判定满足、冲突或未知。限制列额外要求找齐必要风险。比较题需要双方证据，不因命中一方就算成功。

## 逐题Top5、2048预算

| 题目 | 原文预期 | A | B | C |
|---|---|---|---|---|
| D01 想找支持与文档对话、多人使用的AI应用，先推荐有明确文档依据的项目。 | 240: SUPPORTED | 完整 | 完整 | 完整 |
| D02 必须能用于搭建企业内部管理后台和仪表盘，最好是低代码平台，找主要用途贴近的项目。 | 522: SUPPORTED | 完整 | 完整 | 完整 |
| D03 知识库入库前要静态扫描隐藏内容，要求Go编写、不依赖第三方包，并可接入CI，推荐什么项目？ | 546: SUPPORTED | 546: RANKING_OR_BUDGET | 546: RANKING_OR_BUDGET | 546: RANKING_OR_BUDGET |
| D04 希望可视化编排AI工作流，还能写JavaScript和Python自定义代码，有什么项目有这些明确说明？ | 427: SUPPORTED | 完整 | 427: ADMISSION_GAP | 427: ADMISSION_GAP |
| D05 要修改一个React前端、Node后端的AI工具，并研究第三方节点集成，找README明确描述这些模块的项目。 | 244: SUPPORTED | 完整 | 完整 | 完整 |
| D06 想读Go命令行工具的源码，重点研究goroutine worker-pool与channel收集结果，推荐有明确实现说明的项目。 | 546: SUPPORTED | 546: RANKING_OR_BUDGET | 546: RANKING_OR_BUDGET | 546: RANKING_OR_BUDGET |
| D07 给文档CI增加扫描，需要能输出JSON并按告警级别让构建失败，推荐有明确接入示例的工具。 | 546: SUPPORTED | 546: RANKING_OR_BUDGET | 546: RANKING_OR_BUDGET | 546: RANKING_OR_BUDGET |
| D08 想自托管可视化工作流，要求README给出Docker数据持久化的运行示例，请找有依据的项目。 | 427: SUPPORTED | 427: RANKING_OR_BUDGET | 427: ADMISSION_GAP | 427: ADMISSION_GAP |
| V01 我只能使用Node.js 18而且不能升级，按当前README直接安装Flowise，版本条件是否冲突？ | 244: CONFLICT | 完整 | 完整 | 完整 |
| V02 我只能使用Go 1.21且不能升级，按当前README构建Docguard，Go版本是否符合？ | 546: CONFLICT | 完整 | 完整 | 完整 |
| V03 我只接受Go标准库、不允许增加第三方Go包，Docguard的依赖说明能否支持这一条件？ | 546: SUPPORTED | 完整 | 完整 | 完整 |
| V04 我的要求是保证检测出所有提示注入攻击，Docguard的README是否承诺了这种保证？ | 546: CONFLICT | 546: RANKING_OR_BUDGET | 546: RANKING_OR_BUDGET | 546: RANKING_OR_BUDGET |
| V05 筛选条件是README标明Apache License 2.0，Appsmith的许可证名称是否符合？只核对名称。 | 522: SUPPORTED | 完整 | 完整 | 完整 |
| V06 我要二次开发React前端和Node后端，Flowise已有模块说明是否支持这两个技术条件？ | 244: SUPPORTED | 完整 | 完整 | 完整 |
| V07 我不接受只有云服务入口的方案，n8n当前README有没有自托管部署的明确依据？ | 427: SUPPORTED | 完整 | 427: ADMISSION_GAP | 427: ADMISSION_GAP |
| V08 要求默认扫描扩展名就包含pdf且不改配置，Docguard的README默认列表是否满足？ | 546: CONFLICT | 完整 | 完整 | 完整 |
| C01 想做管理后台和仪表盘，比较Appsmith与Flowise README的主要定位，哪些原文直接支持这个选择？ | 522: SUPPORTED, 244: UNKNOWN | 完整 | 244: ADMISSION_GAP | 244: ADMISSION_GAP |
| C02 想学习Go源码中的worker-pool，比较Docguard和Flowise的语言及实现说明，分别有哪些支持或缺失的依据？ | 546: SUPPORTED, 244: UNKNOWN | 546: RANKING_OR_BUDGET | 546: RANKING_OR_BUDGET; 244: RANKING_OR_BUDGET | 546: RANKING_OR_BUDGET; 244: RANKING_OR_BUDGET |
| C03 我想在文档入库前增加静态扫描，比较Docguard和AnythingLLM各自的主要用途，哪个有直接扫描依据？ | 546: SUPPORTED, 240: UNKNOWN | 546: RANKING_OR_BUDGET | 546: RANKING_OR_BUDGET | 546: RANKING_OR_BUDGET |
| C04 需要可视化工作流和Python自定义代码，比较n8n与Flowise README对扩展方式的说明，哪些条件仍缺证据？ | 427: SUPPORTED, 244: UNKNOWN | 427: RANKING_OR_BUDGET; 244: RANKING_OR_BUDGET | 427: ADMISSION_GAP; 244: RANKING_OR_BUDGET | 427: ADMISSION_GAP; 244: RANKING_OR_BUDGET |
| O01 只有4GB内存的服务器能保证生产运行Flowise吗？README里的4096堆参数是否足够证明？ | 244: UNKNOWN | 线索2/2；不足，未测决策 | 线索0/2；不足，未测决策 | 线索2/2；不足，未测决策 |
| O02 要求免费社区版开箱即用支持单点登录，当前Appsmith README足以核实吗？ | 522: UNKNOWN | 线索0/0；不足，未测决策 | 线索0/0；不足，未测决策 | 线索0/0；不足，未测决策 |
| O03 我是Java初学者，每天一小时，能保证两周吃透AnythingLLM源码吗？ | 240: UNKNOWN | 线索1/1；不足，未测决策 | 线索1/1；不足，未测决策 | 线索1/1；不足，未测决策 |
| O04 我要求任何商业场景都可免费使用，n8n README里列出的许可链接能直接证明吗？ | 427: UNKNOWN | 线索1/1；不足，未测决策 | 线索0/1；不足，未测决策 | 线索0/1；不足，未测决策 |
| O05 Appsmith Core免费版必须内置README里Appsmith Agents介绍的能力，当前原文能证明吗？ | 522: UNKNOWN | 线索1/1；不足，未测决策 | 线索0/1；不足，未测决策 | 线索0/1；不足，未测决策 |
| O06 要求工具运行时完全不联网，Docguard所说零外部依赖足以证明这一条件吗？ | 546: UNKNOWN | 线索1/1；不足，未测决策 | 线索1/1；不足，未测决策 | 线索1/1；不足，未测决策 |

## 对照结论

A→B：改善 无；退步 D04, V07, C01。
B→C：改善 无；退步 无。
A→C：改善 无；退步 D04, V07, C01。

本轮使用5个已研究过的仓库、助手标注，不是独立人工评测；多个任务可能使用相同项目事实，不能把它们当成互相独立的统计样本。只测检索证据，不测最终推荐、拒答、追问、导入资格或全库效果。

B组仍含前轮连续区间切分造成的纯标题和短块；本轮不改分块以保持输入不变，A/B差值包含这种表示变化，不全部归因于内容删除。观察题中的线索不证明目标条件，未调用模型就不报告正确拒答率。

复验：python scripts/readme_recommendation_eval.py verify。完整问题、要求强弱、使用方式和原文标签见questions.md及cases.json。
