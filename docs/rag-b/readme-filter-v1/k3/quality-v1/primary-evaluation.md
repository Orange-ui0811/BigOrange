# 扩大分类质量验收 · primary

这些是助手预先审阅的原文标签，不是独立人工金标准；新仓库与复验仓库分开统计。
完成 15/15 批，本阶段用量 15/20，当前总用量 18/20。

| 指标 | 实测 |
|---|---:|
| protocolValidRate | 0.9333 |
| decisionAgreement | 0.7037 |
| keepPrecision | 0.9688 |
| knowledgeRecovery | 0.6739 |
| kindAccuracyOnKeep | 0.9706 |
| scopeAccuracyOnKeep | 1.0 |
| criticalWrongDrop | 0 |
| uncertainWrongKeep | 1 |
| controlErrors | 0 |

建议门槛是否达标：False。跑完不等于通过。
混合版本汇总：False；模型提案一致率 0.7593。
首轮结果未被回归覆盖。

## 分歧清单

### Q1-46-26 · langchain-ai/langgraph · L26–L28

预期 KEEP；模型 KEEP；合并后 REVIEW；类型 USAGE；范围 CORE。
完整安装命令需连同原始前置说明保留

### Q1-46-30 · langchain-ai/langgraph · L30–L31

预期 KEEP；模型 REVIEW；合并后 REVIEW；类型 OTHER；范围 RELATED_PRODUCT。
Deep Agents 为相关包，不能归入 LangGraph 本体

### Q1-335-5 · alphacep/vosk-api · L5–L5

预期 KEEP；模型 REVIEW；合并后 REVIEW；类型 None；范围 None。
保留 50 Mb 原始单位，不换算成已经验证的运行内存

### Q1-335-7 · alphacep/vosk-api · L7–L7

预期 KEEP；模型 REVIEW；合并后 REVIEW；类型 None；范围 None。
语言绑定是完整能力声明

### Q1-335-9 · alphacep/vosk-api · L9–L9

预期 KEEP；模型 REVIEW；合并后 REVIEW；类型 None；范围 None。
应用场景原文，不推导这些业务产品已实现

### Q1-335-11 · alphacep/vosk-api · L11–L11

预期 KEEP；模型 REVIEW；合并后 REVIEW；类型 None；范围 None。
运行平台范围不能代替具体硬件下限

### Q1-523-36 · home-assistant/home-assistant.io · L36–L38

预期 KEEP；模型 KEEP；合并后 REVIEW；类型 USAGE；范围 CORE。
这是网站预览，不是 Home Assistant 核心服务启动

### Q1-523-42 · home-assistant/home-assistant.io · L42–L42

预期 KEEP；模型 KEEP；合并后 KEEP；类型 LIMITATION；范围 CORE。
临时隔离博客是网站构建优化背景

### Q1-523-48 · home-assistant/home-assistant.io · L48–L48

预期 KEEP；模型 KEEP；合并后 REVIEW；类型 USAGE；范围 CORE。
恢复指令的介绍句需与下一原文代码一起保留

### Q1-329-1 · ToolJet/ToolJet · L1–L1

预期 REVIEW；模型 KEEP；合并后 KEEP；类型 PURPOSE；范围 CORE。
社区能力与企业 AI 混合在一个块，先拆分作用域再进入候选证据

### Q1-51-42 · freeCodeCamp/freeCodeCamp · L42–L42

预期 KEEP；模型 REVIEW；合并后 REVIEW；类型 OTHER；范围 UNKNOWN。
认证前置考试要求，不能删掉资格条件

### Q1-51-44 · freeCodeCamp/freeCodeCamp · L44–L44

预期 KEEP；模型 REVIEW；合并后 REVIEW；类型 OTHER；范围 UNKNOWN。
证书展示声明需连同下一段学术诚信例外理解

### Q1-51-46 · freeCodeCamp/freeCodeCamp · L46–L46

预期 KEEP；模型 REVIEW；合并后 REVIEW；类型 OTHER；范围 UNKNOWN。
撤销证书和违规例外必须保留

### Q1-51-48 · freeCodeCamp/freeCodeCamp · L48–L48

预期 KEEP；模型 REVIEW；合并后 REVIEW；类型 OTHER；范围 UNKNOWN。
课程资源合集，不代表其平台实现了所有业务应用

### Q1-267-70 · mikeroyal/Self-Hosting-Guide · L70–L70

预期 KEEP；模型 DROP；合并后 DROP；类型 OTHER；范围 UNKNOWN。
仅作目录导航元数据，不能分类为已有业务管理功能

### Q1-267-71 · mikeroyal/Self-Hosting-Guide · L71–L71

预期 KEEP；模型 DROP；合并后 DROP；类型 OTHER；范围 UNKNOWN。
仅作目录导航元数据，不能分类为协作应用已实现

### Q1-267-73 · mikeroyal/Self-Hosting-Guide · L73–L73

预期 KEEP；模型 DROP；合并后 DROP；类型 OTHER；范围 UNKNOWN。
仅作目录导航元数据，不能分类为备份应用已实现

