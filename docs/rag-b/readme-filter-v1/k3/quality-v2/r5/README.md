# K3-R5：冻结版本真实分类验收

目标：按 R4 已封存的原文、标签、请求和评分约定，判断 R3 分类及最终知识候选是否达到准入门槛。本轮不改分类策略、不重标答案、不切换正式索引。

**已完成执行，质量未通过。** 累计 35/40 次；首次及失败重试诊断分别保存，下一步回到 R3 修复，K4 暂缓。先阅读 [验收结论与修复依据](findings.md)。

用户指令“进行 R5 吧”承接此前明确提出的 34 次首轮＋6 次失败预留、最多 40 次方案。新授权记录绑定 R4 seal、数据集、请求清单和执行代码哈希；历史 50 / 40 / 18 次账本保留，旧集剩余 2 次不复用。

执行保护：

- 首轮 34 个固定请求逐一执行；每次请求与模型实际收到的 system/user 原文核对。
- 选择器预留和实际 HTTP 出站分别持久记账；失败、超时也计数，没有传输自动重试。
- 每个请求先留下持久预留；中断恢复时不会重复已预留请求。中断后缺少完整结果的样本保持失败/待审，不自动重新付费。
- 原始响应、协议结果、耗时、usage 分开保存；不保存密钥。
- 所有批次完成后，用原响应离线回放，同仓库全部批次合并生成 `Result.recommendation`。回放不调用网络。
- 首轮评分永久保留。预留最多重试首轮前六个传输或协议失败，按冻结顺序各一次；不对语义不满意的结果抽样重试。

评分同时检查模型提案和最终候选。正文精确率、范围内决策一致率、知识恢复率目标分别为 95% / 85% / 85%；必要依赖缺失仍使用、范围越界、关键误删、原文错引和不确定放行为零容忍项。导航逐条检查完整标签及地址，未知候选单列。完整口径见 [R4 评分约定](../r4/acceptance-plan.md)。

以下是首次执行顺序的留档；本轮已经完成。现在只需运行最后的 `--verify` 复核，不要重新 `--prepare`，也不要删除材料重开额度。

```powershell
python scripts/run_readme_quality_v2.py --prepare
python scripts/run_readme_quality_v2.py --phase primary
python scripts/score_readme_quality_v2.py --phase primary
# 仅传输/协议失败可消耗预留；首轮已通过的合法语义答案不重跑
python scripts/run_readme_quality_v2.py --phase reserve
python scripts/score_readme_quality_v2.py --phase diagnostic
python scripts/run_readme_quality_v2.py --verify
```

逐次响应、最终批次合并及首轮文件完整性还可通过 `python scripts/verify_readme_quality_v2_execution.py` 只读核验。

`--prepare` 需要先有两项无网络 Java 测试和评分器边界测试证据。不得通过删除账本、修改 runId、重建授权来获得额外额度。R5 开始后用 `--verify` 复核完整性；原 R4 验证器刻意拒绝已有 R5 出站账本的状态，其历史文件保持不变。

结果位置：`primary-evaluation.md/json` 保存首次成绩；`diagnostic-evaluation.md/json` 保存同版本失败重试诊断；`attempts` 保存逐次原始材料；`results` 保存按仓库合并的最终候选。未通过门槛时 K4 继续等待。

本集为助手按用户规则预先标注的目的性样本，不是独立人工金标准或盲测。这里只测知识分类与准入；检索召回、推荐成功率、网站整体耗时在后续另测。
