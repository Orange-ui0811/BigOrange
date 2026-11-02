# 证据不足分流 V2：推荐范围隔离

2026-11-01。延续 [V1](../evidence-gap-v1/plan.md) 的用户条件、项目事实、部分回答、服务不可用和候选覆盖分类。

## 本次补充

1. 有已展示项目时，`verificationGaps` 只属于这些项目，`verificationGapScope=PRESENTED_PROJECTS`。严格模式已排除候选的未知条件不再影响当前推荐。
2. 没有已展示项目时保留候选池内的未知条件，`verificationGapScope=CANDIDATE_POOL`，帮助解释为什么尚未得到已核实候选；不据此断言 GitHub 没有项目。
3. 用户基础已知，项目 `learningFit` 为 UNKNOWN 或 SCOPE_UNVERIFIED 时，生成项目侧的 `LEARNING_SCOPE_UNVERIFIED`；不重复询问用户基础。CHALLENGING 和 CAUTION 仍是已有资料提示的门槛，不混同为未知事实。
4. 旧卡片 `matchStatus=NEEDS_VERIFICATION` 即使带有一条引用，也不能视为必要条件全部核实。缺乏细分缺口时保留 `REQUIRED_CONDITIONS_UNVERIFIED`。

## 验收与证据

原218项离线检查加4条范围回归，预期222项，失败、错误、跳过均为0。10个业务场景分别通过原执行器和 LangGraph4j，导出20条模拟回复。模型为测试替身，外部模型和 Embedding 调用均为0；不声明真实语义准确率。

V1结果与构建收据保持原路径和哈希；其31份源码输入已在修改前归档到 `previous-v1-sources`。新版验证器检查归档输入、V1原输出及前序RAG验收，再校验本版输入和输出。前端本轮未改动，沿用并校验V1成功构建记录。

运行：`python scripts/check_customer_evidence_gap_scope.py`。封存后只读复验：`python scripts/check_customer_evidence_gap_scope.py --verify`。

## 交付边界

下一动作 `VERIFY_PROJECT_FACTS` 表示系统需要继续查项目证据，并不代表已经自动抓取外部文档或完成在线核实。个人适配需求依赖现有意图理解与任务追问；纯事实问答覆盖由已有VERIFY调用给出并由服务端严格校验。本轮不接新资料来源、不切生产索引、不改数据库、不启动或重启网站服务。
