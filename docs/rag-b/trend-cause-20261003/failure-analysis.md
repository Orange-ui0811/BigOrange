# 保留的失败调用

文件：`replay-data_analysis-2-B.json`。客户端耗时 85.49 秒。没有重试、没有补成功样本。

本地后端日志 20:07:17，requestId `3a6cffb7`：

```
系统异常: AI输出未完成: length
java.lang.IllegalStateException: AI输出未完成: length
at com.orangejd.server.service.TrendModelClient.completeMeasured(TrendModelClient.java:52)
```

这次是模型输出不完整，不是检索、候选过滤、证据压缩、JSON 组装或来源绑定失败。快照重放没有执行这些检索及业务步骤。

现有客户端先检查 finish_reason，再读取 usage，因此这一失败的返回用量没有进入实验响应。此次费用未知，不能记为 0，已知费用汇总不代表全部调用费用。非流式接口也没有供应商内部排队、预填充、思考和解码分阶段时长。

实验脚本曾将缺少成功响应误报为 `Replay payload changed`，导致停止调度；随后修正为只对成功返回请求体进行哈希核验。恢复调度时跳过全部已有成功及失败文件，只执行剩余计划项，仍遵守最多 32 次调用，不重放失败项。没有发现成功快照请求体改变。

工程改进建议：在校验输出完整性前提取返回版本、finish_reason 和 usage；失败响应也保留这些字段并计入运行用量。未从本轮失败样本恢复到的数据不作推算。
