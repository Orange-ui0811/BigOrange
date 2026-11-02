# K3-R4：新版分类验收集已冻结

日期：2026-10-29。状态：**调用前冻结与离线完整性检查完成，R5 真实质量验收未运行。**

本阶段将用户确认的项目推荐范围转换成可核对的题库、原文标签和实际请求。生产分类策略沿用 R3，没有因本集标签修改分类器。材料来源是本地已保存的 README，不是重新抓取的远端最新版。

## 冻结成果

| 项目 | 数量 / 状态 |
|---|---|
| 新仓库 | 12 个，与此前 46 个审计或质量验收仓库不重叠 |
| 原文标注 | 147 条，全部由助手按已确认规则标注 |
| 模型目标 | 123 条；全部实际可执行批次均纳入，没有看模型结果挑批次 |
| 规则、导航及范围控制 | 24 条，独立统计 |
| 关键项 | 61 条，包含许可、版别、版本、截断和引用数据中的攻击短语 |
| 有语义依赖的标注 | 43 条；标注依赖独立于程序依赖图 |
| 首轮请求 | 34 次，每次最多 4 个目标，输出上限 2048 Token |
| 失败预留 | 建议 6 次，总提议上限 40 次；尚未授权 |
| 新增真实请求 / 索引写入 | 0 / 0 |

助手标注、定向选样、已阅读原文；不是独立人工金标准、盲测或随机抽样，不能据此估计全库准确率。

## 样本覆盖

| 仓库 | 主要验收问题 |
|---|---|
| Mintplex-Labs/anything-llm | HTML 内有效用途、桌面平台支持、许可证徽章、托管入口与末尾截断 |
| FlowiseAI/Flowise | Node 版本、npm/Compose/源码启动、代码配套说明、条件性堆大小与不同操作系统语法 |
| milvus-io/milvus | Milvus / Milvus Lite 混合归属、Zilliz 托管产品、Python SDK 与服务本体区分 |
| PaddlePaddle/PaddleOCR | 工具包定位、未读取的许可徽章与截断功能句 |
| microsoft/IoT-For-Beginners | 资源与社区宣传不足以证明可运行项目；克隆命令不能代替推荐资格 |
| lettier/3d-game-shaders-for-beginners | 教学仓库中可研究软件部分、仅适用于代码扩展名的许可、目录导航 |
| open-webui/open-webui | 企业方案的品牌/SLA/LTS 归属、普通功能声明、截图与文档入口 |
| MarlinFirmware/Configurations | 配置集合的资格不确定、版本与分支匹配要求、截断表格 |
| awesome-selfhosted/awesome-selfhosted | 资源集合范围外、备份/CRM 目录不能冒充自有功能 |
| n8n-io/n8n | Node 前提、可选部署方式、混合企业能力与许可、导航、有效但受许可待审阻断的内容 |
| appsmithorg/appsmith | 本体与 Appsmith Agents、部署/开发指南入口、正文许可与贡献宣传 |
| ByteBunny777/Docguard | Go 版本、启发式检测限制、参数默认值、引用的注入示例必须作为数据 |

曾审阅 SiYuan，但它单独需要 29 批，会占据当前请求上限的大部分；选用 Open WebUI 直接验证企业方案边界。这个选择发生在真实调用前，理由已保存。长文原文映射仍由 R3 的 303 份离线回归覆盖，本集不声称覆盖全部长文分类风险。

## 怎样阅读与复核

1. [gold-labels.md](gold-labels.md)：逐条原文、位置、预期去向、范围与必要依赖。
2. [frozen-dataset.json](frozen-dataset.json)：机器可读标签。B 正文 82 条；N 导航 26 条；A 范围外有效事实 2 条；D 噪声 17 条；R 待复核 19 条；H 有用但条件待审 1 条。
3. [sources.json](sources.json)：12 份完整本地原文、版本、原始块和 UTF-16 定位。
4. [requests.json](requests.json)：34 个真实 Java 规划批次，包括 system/user 原样输入、目标、上下文、输入上界与缓存键。
5. [provider-config.json](provider-config.json)：沿用现有后端的模型、端点和不含密钥的身份指纹；关闭 thinking 与工具，超时 35 秒。
6. [freeze-manifest.json](freeze-manifest.json)：冻结代码、协议、资料与旧账本哈希。
7. [verification.json](verification.json)：独立完整性检查回执，不是模型准确率报告。
8. [r4-seal.json](r4-seal.json)：将原文标签、请求、评分约定和离线导出证据一并封存；R5 应绑定本清单，避免只核对标签却悄悄更换评分方法。

只读复核：

```powershell
cd D:\Orangejd
python scripts/verify_readme_quality_v2_freeze.py
```

禁止重复执行旧 v1 质量脚本来跑本集。新准备脚本在已冻结时拒绝覆盖。实现或配置变化后，应保留本版首次成绩并建立新版本，不在拿到模型输出后回改本集答案。

标注自检在调用前发现过一项依赖矛盾：`Q2-427-53` 的内容有效，但必需许可块待审，因此最终必须待复核。已将它单列为 H，不要求系统绕过许可去提高恢复率。原稿及原因保存在 `_experiment_staging/readme-quality-v2/draft-01`。最终冻结集已经通过依赖一致性检查。

## R5 评分口径

严格使用 [评分与执行约定](acceptance-plan.md)，模型提案与 `Result.recommendation` 的最终结果分别报告。REVIEW 和失败不能从原分母中消失；导航不能混入正文，也不能用只返回 KEEP 充当知识恢复。

预期范围由本集事先确定，不根据预测自行缩小。最终去向、原文及必要依赖、每块适用性与 scopes 都要验证；只看枚举合法不足以证明分类正确。

## 下一步与调用安排

[execution-proposal.json](execution-proposal.json) 是请求预算提议，`authorized=false`，不是新授权。34 次首轮的输入字节上界加输出预留合计 350396；最多 40 次的保守累计预留上界 561920。它们不是提供方实际 Token 用量或费用估计。

此前 v1 授权是 18/20，剩余 2 次仍绑定旧数据集。R5 需要针对本冻结集明确授权；应使用新的实际出站账本并保留所有旧记录。完成新执行入口和出站前校验后，才可按实际批准的次数运行。

R4 的离线 Java 导出测试 1 项通过；它使用 `--network none`，传输接口若被调用会直接失败。该结果不替代 R3 的 66 项实现测试，也不替代 R5 的 95% / 85% / 85% 分类质量门槛。K4 正式入库和 K5 检索对比继续等待质量验收。
