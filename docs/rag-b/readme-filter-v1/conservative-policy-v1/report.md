# 保守准入 V5：修改与推荐检索回归

5份原有README，20道主检索题及6道不足观察题。复用全部查询向量与已保存分类，不调用云端模型。A的正文、向量和排名均与原实验完全一致。

| 方案 | 找到候选 /8 | 找候选证据完整 /8 | 核验证据完整 /8 | 比较证据完整 /4 | 主集事实完整 /20 | 加必要限制 /20 |
|---|---:|---:|---:|---:|---:|---:|
| A | 8/8 | 6/8 | 7/8 | 2/4 | 15/20 | 12/20 |
| B_old | 6/8 | 5/8 | 6/8 | 1/4 | 12/20 | 9/20 |
| C_old | 6/8 | 5/8 | 6/8 | 0/4 | 11/20 | 9/20 |
| B_v5 | 8/8 | 5/8 | 7/8 | 1/4 | 13/20 | 10/20 |
| C_v5 | 8/8 | 5/8 | 7/8 | 0/4 | 12/20 | 10/20 |

以上统一为Top5、2048个BGE Token预算。仅测证据找齐；未执行最终推荐判断、拒答、追问和许可资格判断。

## 实现调整

- 准入V5按具体事实和依赖重新检查；原始REVIEW块不自动放行。
- 许可状态单列：待核实的许可名称与链接不阻断独立功能/安装事实；明确的运行、非生产、版别限制继续绑定。许可资格本身没有因此获得批准。
- 明确的本机安装声明可作为原文事实；只有链接的导航仍隔离。关联产品声明不能变成核心产品能力。
- 结构分块保留原文标题上下文，停止把纯标题当独立向量。引用以多个原文区间保存，派生分隔符没有原文位置。
- B_v5与C_v5共享修改后的批准范围。B_v5同时修改了短块表示，因此旧B到新B的差值包含准入与分块两个因素，不宣称单因素因果。

## 逐题结果

| 题号 | A | 旧B | 旧C | V5 B | V5 C |
|---|---|---|---|---|---|
| D01 | 完整 | 完整 | 完整 | 完整 | 完整 |
| D02 | 完整 | 完整 | 完整 | 完整 | 完整 |
| D03 | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET |
| D04 | 完整 | 427:ADMISSION_GAP | 427:ADMISSION_GAP | 427:ADMISSION_GAP | 427:ADMISSION_GAP |
| D05 | 完整 | 完整 | 完整 | 完整 | 完整 |
| D06 | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET |
| D07 | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET |
| D08 | 427:RANKING_OR_BUDGET | 427:ADMISSION_GAP | 427:ADMISSION_GAP | 427:RANKING_OR_BUDGET | 427:RANKING_OR_BUDGET |
| V01 | 完整 | 完整 | 完整 | 完整 | 完整 |
| V02 | 完整 | 完整 | 完整 | 完整 | 完整 |
| V03 | 完整 | 完整 | 完整 | 完整 | 完整 |
| V04 | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET |
| V05 | 完整 | 完整 | 完整 | 完整 | 完整 |
| V06 | 完整 | 完整 | 完整 | 完整 | 完整 |
| V07 | 完整 | 427:ADMISSION_GAP | 427:ADMISSION_GAP | 完整 | 完整 |
| V08 | 完整 | 完整 | 完整 | 完整 | 完整 |
| C01 | 完整 | 244:ADMISSION_GAP | 244:ADMISSION_GAP | 244:ADMISSION_GAP | 244:ADMISSION_GAP |
| C02 | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET; 244:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET; 244:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET; 244:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET; 244:RANKING_OR_BUDGET |
| C03 | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET | 546:RANKING_OR_BUDGET |
| C04 | 427:RANKING_OR_BUDGET; 244:RANKING_OR_BUDGET | 427:ADMISSION_GAP; 244:RANKING_OR_BUDGET | 427:ADMISSION_GAP; 244:RANKING_OR_BUDGET | 427:ADMISSION_GAP; 244:RANKING_OR_BUDGET | 427:ADMISSION_GAP; 244:RANKING_OR_BUDGET |
| O01 | 仅线索，未测决策 | 仅线索，未测决策 | 仅线索，未测决策 | 仅线索，未测决策 | 仅线索，未测决策 |
| O02 | 仅线索，未测决策 | 仅线索，未测决策 | 仅线索，未测决策 | 仅线索，未测决策 | 仅线索，未测决策 |
| O03 | 仅线索，未测决策 | 仅线索，未测决策 | 仅线索，未测决策 | 仅线索，未测决策 | 仅线索，未测决策 |
| O04 | 仅线索，未测决策 | 仅线索，未测决策 | 仅线索，未测决策 | 仅线索，未测决策 | 仅线索，未测决策 |
| O05 | 仅线索，未测决策 | 仅线索，未测决策 | 仅线索，未测决策 | 仅线索，未测决策 | 仅线索，未测决策 |
| O06 | 仅线索，未测决策 | 仅线索，未测决策 | 仅线索，未测决策 | 仅线索，未测决策 | 仅线索，未测决策 |

## 成对变化

B_old → B_v5：改善 V07；退步 无。
C_old → C_v5：改善 V07；退步 无。
A → C_v5：改善 无；退步 D04, C01。

## 适用边界

仍是5个已研究仓库上的助手标注回归，不能证明全库效果或策略最优；同一事实可能被多道题引用。风险限制的排名问题没有通过定向查询或重排来掩盖，原本待审的n8n功能列表、Flowise定位等仍保留为遗漏。

本轮不切换网站索引。当前入口ReadmeKnowledgePreparation负责准备预览，实际发布、最终许可核实及风险补查仍属后续工作。

复验：python scripts/readme_conservative_eval.py verify。请求清单、独立账本、原文映射、待审项、逐题指标均保存在本目录。
