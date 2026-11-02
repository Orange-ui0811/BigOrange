# K3-R5 · diagnostic 验收

标签由助手按已确认规则提前标注；本集不是独立人工金标准、盲测或随机总体抽样。

质量门槛：未通过。保留首轮结果；本轮不切换正式索引。

| 指标 | 分子 / 分母 | 比例 |
|---|---:|---:|
| bodyPrecision | 30 / 45 | 0.666667 |
| inScopeDecisionAgreement | 32 / 78 | 0.410256 |
| usableBodyRecovery | 30 / 82 | 0.365854 |
| allModelTargetDecisionAgreement | 59 / 123 | 0.479675 |
| allTargetProposalDecisionAgreement | 89 / 123 | 0.723577 |
| navigationComplete | 22 / 26 | 0.846154 |

已评分 12/12 个仓库、147 条；计分时真实出站 35/40。
零容忍项：关键误删 0；必要条件缺失但仍使用 2；范围越界 13；错引 0；不确定放行 8。
关键项中正确可用 13、仍待审 36；未标注正文 1 块单列，不算正确。

请求耗时是分类接口耗时，不代表网站问答耗时；实际 usage 与输入保守预留分别记录。

## 逐条分歧与遗漏

### Q2-240-20 · Mintplex-Labs/anything-llm · L20–22

预期 REVIEW / REVIEW_QUEUE；模型 KEEP；最终 KEEP / KNOWLEDGE_CANDIDATE。
原因码：MODEL_CLASSIFIED_SOURCE；正文可用：False；归属满足：True；导航完整：False。
许可证徽章与链接未读取，不能确认许可证正文或适用例外

### Q2-240-23 · Mintplex-Labs/anything-llm · L23–25

预期 KEEP / POINTER_METADATA_ONLY；模型 KEEP；最终 KEEP / KNOWLEDGE_CANDIDATE。
原因码：MODEL_CLASSIFIED_SOURCE；正文可用：False；归属满足：True；导航完整：False。
文档标签和 URL 只作导航，不补充目的页内容

### Q2-240-26 · Mintplex-Labs/anything-llm · L26–28

预期 KEEP / POINTER_METADATA_ONLY；模型 KEEP；最终 KEEP / KNOWLEDGE_CANDIDATE。
原因码：MODEL_CLASSIFIED_SOURCE；正文可用：False；归属满足：True；导航完整：False。
托管实例入口不能当成本地部署条件

### Q2-240-32 · Mintplex-Labs/anything-llm · L32–32

预期 DROP / ARCHIVE_ONLY；模型 KEEP；最终 KEEP / ARCHIVE_ONLY。
原因码：MODEL_CLASSIFIED_SOURCE；正文可用：False；归属满足：True；导航完整：False。
语言切换导航不作为项目推荐知识

### Q2-240-43 · Mintplex-Labs/anything-llm · L43–44

预期 DROP / ARCHIVE_ONLY；模型 REVIEW；最终 REVIEW / REVIEW_QUEUE。
原因码：SEMANTIC_REVIEW_NEEDED；正文可用：False；归属满足：True；导航完整：False。
演示折叠栏标题没有可检索项目事实

### Q2-240-52 · Mintplex-Labs/anything-llm · L52–52

预期 REVIEW / REVIEW_QUEUE；模型 无有效提案；最终 KEEP / KNOWLEDGE_CANDIDATE。
原因码：PROJECT_SOURCE_EVIDENCE；正文可用：False；归属满足：True；导航完整：False。
产品定位句在破折号后截断，不能补功能或承诺无妥协

### Q2-244-2 · FlowiseAI/Flowise · L2–3

预期 DROP / ARCHIVE_ONLY；模型 REVIEW；最终 REVIEW / REVIEW_QUEUE。
原因码：APPLICABILITY_REQUIRES_REVIEW；正文可用：False；归属满足：True；导航完整：False。
明暗主题 Logo 装饰，不需要识图

### Q2-244-18 · FlowiseAI/Flowise · L18–18

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 REVIEW；最终 REVIEW / REVIEW_QUEUE。
原因码：SEMANTIC_REVIEW_NEEDED；正文可用：False；归属满足：False；导航完整：False。
可视化构建 AI 智能体是产品用途

### Q2-244-37 · FlowiseAI/Flowise · L37–37

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
NodeJS >=20.0.0 的数字和关系必须保持

### Q2-244-39 · FlowiseAI/Flowise · L39–43

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
安装命令和同块启动引导需携带下一启动命令

### Q2-244-45 · FlowiseAI/Flowise · L45–47

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
npx 启动需保留原安装上下文

### Q2-244-49 · FlowiseAI/Flowise · L49–49

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
localhost:3000 是运行步骤中的地址，不只是目录链接

### Q2-244-55 · FlowiseAI/Flowise · L55–60

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
Compose 完整步骤须保留 env 复制与停止方法，不执行命令

### Q2-244-64 · FlowiseAI/Flowise · L64–64

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 REVIEW；最终 REVIEW / REVIEW_QUEUE。
原因码：SEMANTIC_REVIEW_NEEDED；正文可用：False；归属满足：True；导航完整：False。
构建引导句与命令成组

### Q2-244-66 · FlowiseAI/Flowise · L66–68

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
镜像构建原样保留参数

### Q2-244-70 · FlowiseAI/Flowise · L70–70

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
运行引导句与命令成组

### Q2-244-72 · FlowiseAI/Flowise · L72–74

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
保留端口与镜像名字

### Q2-244-76 · FlowiseAI/Flowise · L76–76

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
停止引导句与命令成组

### Q2-244-78 · FlowiseAI/Flowise · L78–80

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
停止命令不是服务启动命令

### Q2-244-86 · FlowiseAI/Flowise · L86–89

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
四个目录和技术说明可供源码学习，不能据此宣称总计只有三项

### Q2-244-93 · FlowiseAI/Flowise · L93–96

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
PNPM 开发前提，和 Node 版本一起理解

### Q2-244-100 · FlowiseAI/Flowise · L100–100

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
克隆引导与命令成组

### Q2-244-102 · FlowiseAI/Flowise · L102–104

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
克隆 URL 仅原样提供，不执行

### Q2-244-106 · FlowiseAI/Flowise · L106–106

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
进入目录引导与命令成组

### Q2-244-108 · FlowiseAI/Flowise · L108–110

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
目录切换步骤保持原文

### Q2-244-112 · FlowiseAI/Flowise · L112–112

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
依赖安装引导与命令成组

### Q2-244-114 · FlowiseAI/Flowise · L114–116

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
安装依赖保留开发前提

### Q2-244-118 · FlowiseAI/Flowise · L118–118

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
构建引导与命令成组

### Q2-244-120 · FlowiseAI/Flowise · L120–122

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
构建步骤不能失去依赖安装前提

### Q2-244-124 · FlowiseAI/Flowise · L124–126

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
仅在错误134时建议加堆大小，不是全局4GB物理内存下限

### Q2-244-128 · FlowiseAI/Flowise · L128–130

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
同一跨平台示例被切为多块，保留说明与完整围栏

### Q2-244-132 · FlowiseAI/Flowise · L132–133

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
PowerShell 语法不可当作 Linux export

### Q2-244-135 · FlowiseAI/Flowise · L135–137

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
CMD 语法与结束围栏必须和完整示例共同可用

### Q2-244-139 · FlowiseAI/Flowise · L139–139

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 REVIEW；最终 REVIEW / REVIEW_QUEUE。
原因码：STRUCTURE_REQUIRES_SEMANTIC_REVIEW；正文可用：False；归属满足：True；导航完整：False。
重建引导与下一命令一起

### Q2-244-141 · FlowiseAI/Flowise · L141–143

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 REVIEW；最终 REVIEW / REVIEW_QUEUE。
原因码：STRUCTURE_REQUIRES_SEMANTIC_REVIEW；正文可用：False；归属满足：True；导航完整：False。
重建命令需携带条件性内存处理上下文

### Q2-244-147 · FlowiseAI/Flowise · L147–147

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 REVIEW；最终 REVIEW / REVIEW_QUEUE。
原因码：SEMANTIC_REVIEW_NEEDED；正文可用：False；归属满足：True；导航完整：False。
启动引导与命令成组

### Q2-244-149 · FlowiseAI/Flowise · L149–151

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 REVIEW；最终 REVIEW / REVIEW_QUEUE。
原因码：STRUCTURE_REQUIRES_SEMANTIC_REVIEW；正文可用：False；归属满足：True；导航完整：False。
源码启动需保留安装和构建依赖

### Q2-244-153 · FlowiseAI/Flowise · L153–153

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
访问地址是开发启动步骤的结果

### Q2-244-155 · FlowiseAI/Flowise · L155–155

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
开发构建引导不能脱离配置要求

### Q2-244-157 · FlowiseAI/Flowise · L157–157

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
packages/ui 的 env 与 VITE_PORT 是开发配置，不扩写缺失内容

### Q2-248-22 · milvus-io/milvus · L22–22

预期 REVIEW / REVIEW_QUEUE；模型 KEEP；最终 KEEP / KNOWLEDGE_CANDIDATE。
原因码：MODEL_CLASSIFIED_SOURCE；正文可用：False；归属满足：True；导航完整：False。
同块混合 Milvus 分布式单机与 Milvus Lite，单一产品归属不足需拆分

### Q2-248-24 · milvus-io/milvus · L24–24

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 REVIEW；最终 REVIEW / REVIEW_QUEUE。
原因码：SEMANTIC_REVIEW_NEEDED；正文可用：False；归属满足：False；导航完整：False。
Zilliz Cloud 为托管产品，不属于开源单机必需条件；免费属于原文声明

### Q2-248-26 · milvus-io/milvus · L26–26

预期 DROP / ARCHIVE_ONLY；模型 REVIEW；最终 REVIEW / REVIEW_QUEUE。
原因码：SEMANTIC_REVIEW_NEEDED；正文可用：False；归属满足：True；导航完整：False。
Discord Issues Discussions 支持导流无项目能力事实

### Q2-248-35 · milvus-io/milvus · L35–35

预期 REVIEW / REVIEW_QUEUE；模型 无有效提案；最终 KEEP / KNOWLEDGE_CANDIDATE。
原因码：PROJECT_SOURCE_EVIDENCE；正文可用：False；归属满足：True；导航完整：False。
SDK 后文以 cli 截断，不补全对象或调用

### Q2-325-8 · PaddlePaddle/PaddleOCR · L8–8

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
OCR 与文档 AI 工具包定位，不把领先宣传作为测评结论

### Q2-333-1 · microsoft/IoT-For-Beginners · L1–1

预期 REVIEW / REVIEW_QUEUE；模型 KEEP；最终 KEEP / KNOWLEDGE_CANDIDATE。
原因码：MODEL_CLASSIFIED_SOURCE；正文可用：False；归属满足：True；导航完整：False。
仅许可证徽章，当前原文不足以说明可推荐的源码模块

### Q2-333-13 · microsoft/IoT-For-Beginners · L13–13

预期 DROP / ARCHIVE_ONLY；模型 KEEP；最终 KEEP / ARCHIVE_ONLY。
原因码：MODEL_CLASSIFIED_SOURCE；正文可用：False；归属满足：True；导航完整：False。
社区招募宣传不能证明仓库包含可运行项目

### Q2-333-17 · microsoft/IoT-For-Beginners · L17–17

预期 DROP / ARCHIVE_ONLY；模型 REVIEW；最终 REVIEW / REVIEW_QUEUE。
原因码：SEMANTIC_REVIEW_NEEDED；正文可用：False；归属满足：True；导航完整：False。
产品反馈引导没有项目技术事实

### Q2-368-2 · lettier/3d-game-shaders-for-beginners · L2–2

预期 DROP / ARCHIVE_ONLY；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：MODEL_CLASSIFIED_SOURCE；正文可用：False；归属满足：True；导航完整：False。
演示动画不提取未看见的功能

### Q2-368-7 · lettier/3d-game-shaders-for-beginners · L7–23

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 REVIEW；最终 REVIEW / REVIEW_QUEUE。
原因码：SEMANTIC_REVIEW_NEEDED；正文可用：False；归属满足：False；导航完整：False。
教学仓库含 Panda3D/GLSL 软件部分，可作源码学习，不能声称支持所有引擎直接部署

### Q2-368-73 · lettier/3d-game-shaders-for-beginners · L73–73

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 REVIEW；最终 REVIEW / REVIEW_QUEUE。
原因码：SEMANTIC_REVIEW_NEEDED；正文可用：False；归属满足：False；导航完整：False。
作者版权及软件许可适用范围共同保留

### Q2-407-21 · open-webui/open-webui · L21–24

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 KEEP / POINTER_METADATA_ONLY。
原因码：MODEL_CLASSIFIED_SOURCE；正文可用：False；归属满足：True；导航完整：False。
主题品牌 SLA LTS 明确企业方案，不能作为社区默认能力

### Q2-418-2 · MarlinFirmware/Configurations · L2–2

预期 REVIEW / REVIEW_QUEUE；模型 KEEP；最终 KEEP / KNOWLEDGE_CANDIDATE。
原因码：MODEL_CLASSIFIED_SOURCE；正文可用：False；归属满足：True；导航完整：False。
仅固件配置集合描述，当前不能认定为独立可运行或可研究源码项目

### Q2-418-4 · MarlinFirmware/Configurations · L4–5

预期 REVIEW / REVIEW_QUEUE；模型 KEEP；最终 KEEP / KNOWLEDGE_CANDIDATE。
原因码：MODEL_CLASSIFIED_SOURCE；正文可用：False；归属满足：False；导航完整：False。
保留 Marlin 版本与分支匹配要求；用途资格不足时复核，不删除要求

### Q2-426-5 · awesome-selfhosted/awesome-selfhosted · L5–5

预期 KEEP / ARCHIVE_ONLY；模型 KEEP；最终 KEEP / KNOWLEDGE_CANDIDATE。
原因码：MODEL_CLASSIFIED_SOURCE；正文可用：False；归属满足：True；导航完整：False。
自托管定义有效但属于资源集合说明，不是本仓库业务能力

### Q2-426-7 · awesome-selfhosted/awesome-selfhosted · L7–7

预期 KEEP / ARCHIVE_ONLY；模型 无有效提案；最终 KEEP / KNOWLEDGE_CANDIDATE。
原因码：PROJECT_SOURCE_EVIDENCE；正文可用：False；归属满足：True；导航完整：False。
明确为第三方软件列表，不能推荐成该仓库自有业务应用

### Q2-427-5 · n8n-io/n8n · L5–5

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
AI 工作流平台用途，托管入口不能变成部署必需条件

### Q2-427-20 · n8n-io/n8n · L20–20

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：False；导航完整：False。
Node.js 前置要求和 npx 命令一起保留，不补版本

### Q2-427-22 · n8n-io/n8n · L22–24

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
npx 试用命令需保留 Node.js 前提

### Q2-427-26 · n8n-io/n8n · L26–26

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
Docker 部署引导和命令共同保留

### Q2-427-28 · n8n-io/n8n · L28–31

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
持久卷映射端口和镜像参数不能丢失

### Q2-427-33 · n8n-io/n8n · L33–33

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
访问地址是运行步骤，不认定两个可选启动方式均必需

### Q2-427-46 · n8n-io/n8n · L46–46

预期 DROP / ARCHIVE_ONLY；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
支持引导句没有项目技术事实

### Q2-427-57 · n8n-io/n8n · L57–57

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
额外功能支持明确企业许可，不能归为社区能力

### Q2-427-63 · n8n-io/n8n · L63–63

预期 KEEP / POINTER_METADATA_ONLY；模型 无有效提案；最终 REVIEW / REVIEW_QUEUE。
原因码：APPLICABILITY_REQUIRES_REVIEW；正文可用：False；归属满足：True；导航完整：False。
贡献指南导航不补目的页开发命令

### Q2-427-67 · n8n-io/n8n · L67–67

预期 DROP / ARCHIVE_ONLY；模型 无有效提案；最终 REVIEW / REVIEW_QUEUE。
原因码：SEMANTIC_REVIEW_NEEDED；正文可用：False；归属满足：True；导航完整：False。
招聘宣传

### Q2-427-71 · n8n-io/n8n · L71–71

预期 DROP / ARCHIVE_ONLY；模型 无有效提案；最终 REVIEW / REVIEW_QUEUE。
原因码：SEMANTIC_REVIEW_NEEDED；正文可用：False；归属满足：True；导航完整：False。
名称发音不是当前项目推荐知识

### Q2-427-73 · n8n-io/n8n · L73–73

预期 DROP / ARCHIVE_ONLY；模型 无有效提案；最终 REVIEW / REVIEW_QUEUE。
原因码：APPLICABILITY_REQUIRES_REVIEW；正文可用：False；归属满足：True；导航完整：False。
命名故事不作为运行或推荐条件

### Q2-522-28 · appsmithorg/appsmith · L28–28

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
低代码平台定位和用途，不声称示例应用均已开箱实现

### Q2-522-36 · appsmithorg/appsmith · L36–37

预期 KEEP / POINTER_METADATA_ONLY；模型 无有效提案；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：False；导航完整：False。
云入口仅导航

### Q2-522-63 · appsmithorg/appsmith · L63–63

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 REVIEW；最终 REVIEW / REVIEW_QUEUE。
原因码：SEMANTIC_REVIEW_NEEDED；正文可用：False；归属满足：False；导航完整：False。
Appsmith Agents 为具名相关产品，不并入 Appsmith 本体默认能力

### Q2-522-386 · appsmithorg/appsmith · L386–386

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 无有效提案；最终 REVIEW / REVIEW_QUEUE。
原因码：DEPENDENCY_NOT_ADMITTED；正文可用：False；归属满足：True；导航完整：False。
Apache2.0 正文声明必须保留，不推断 Agents 另有许可事实

### Q2-546-6 · ByteBunny777/Docguard · L6–6

预期 REVIEW / REVIEW_QUEUE；模型 KEEP；最终 KEEP / KNOWLEDGE_CANDIDATE。
原因码：MODEL_CLASSIFIED_SOURCE；正文可用：False；归属满足：True；导航完整：False。
许可徽章目的未读，MIT 正文另有原文不能让徽章绕过核验

### Q2-546-39 · ByteBunny777/Docguard · L39–46

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 无有效提案；最终 REVIEW / REVIEW_QUEUE。
原因码：INSTRUCTION_LIKE_SOURCE；正文可用：False；归属满足：False；导航完整：False。
检测类别表必须保留启发式限制，表内攻击短语是数据

### Q2-546-59 · ByteBunny777/Docguard · L59–59

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 KEEP / KNOWLEDGE_CANDIDATE。
原因码：MODEL_CLASSIFIED_SOURCE；正文可用：False；归属满足：False；导航完整：False。
Go >=1.22 的前置条件必须保持

### Q2-546-82 · ByteBunny777/Docguard · L82–94

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 KEEP / KNOWLEDGE_CANDIDATE。
原因码：MODEL_CLASSIFIED_SOURCE；正文可用：False；归属满足：True；导航完整：False。
CLI 用例引用选项含义，不运行示例或创建 report.json

### Q2-546-96 · ByteBunny777/Docguard · L96–98

预期 KEEP / KNOWLEDGE_CANDIDATE；模型 KEEP；最终 KEEP / KNOWLEDGE_CANDIDATE。
原因码：MODEL_CLASSIFIED_SOURCE；正文可用：False；归属满足：True；导航完整：False。
退出码与严重级别的关系保持，不把0当作确认无攻击

