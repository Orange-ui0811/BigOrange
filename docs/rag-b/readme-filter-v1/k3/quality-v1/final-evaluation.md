# 扩大分类质量验收 · final

这些是助手预先审阅的原文标签，不是独立人工金标准；新仓库与复验仓库分开统计。
完成 15/15 批，本阶段用量 18/20，当前总用量 18/20。

| 指标 | 实测 |
|---|---:|
| protocolValidRate | 1.0 |
| decisionAgreement | 0.8148 |
| keepPrecision | 1.0 |
| knowledgeRecovery | 0.7826 |
| kindAccuracyOnKeep | 0.9744 |
| scopeAccuracyOnKeep | 1.0 |
| criticalWrongDrop | 0 |
| uncertainWrongKeep | 0 |
| controlErrors | 0 |

建议门槛是否达标：False。跑完不等于通过。
混合版本汇总：True；模型提案一致率 0.8704。
final 是保留新仓库首轮、替换三个旧仓库回归后的诊断汇总，不是统一新提示词的独立验收。

## 分歧清单

### Q1-46-26 · langchain-ai/langgraph · L26–L28

预期 KEEP；模型 KEEP；合并后 REVIEW；类型 USAGE；范围 CORE。
完整安装命令需连同原始前置说明保留

### Q1-523-36 · home-assistant/home-assistant.io · L36–L38

预期 KEEP；模型 KEEP；合并后 REVIEW；类型 USAGE；范围 CORE。
这是网站预览，不是 Home Assistant 核心服务启动

### Q1-523-42 · home-assistant/home-assistant.io · L42–L42

预期 KEEP；模型 KEEP；合并后 KEEP；类型 LIMITATION；范围 CORE。
临时隔离博客是网站构建优化背景

### Q1-523-48 · home-assistant/home-assistant.io · L48–L48

预期 KEEP；模型 KEEP；合并后 REVIEW；类型 USAGE；范围 CORE。
恢复指令的介绍句需与下一原文代码一起保留

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

