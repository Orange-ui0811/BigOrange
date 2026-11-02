# B版Reranker接入

接入路径：`RagBSearchService`在活动版本与业务硬条件过滤、去重之后调用`RagBReranker`，随后进行原有项目多样性选择、TopK截断、价格证据检查和回答策略判断。无需重建知识库或向量索引。

模型默认`bge-reranker-v2-m3`，处理最多80个候选，每批20条，整轮共享5秒超时，并发最多2轮，无HTTP重试。Gitee真实接口已核实单次documents上限为25，见attempt-1/provider-limit-probe.json。空候选/单候选不调用模型。过大请求不截断事实，直接回退；任一批异常、超时、忙碌、凭据缺失、返回索引不完整或重复时，整轮保持原始排序，不混用部分模型分数。

`score`仍为原检索融合分数；`rerankScore`为模型分数，两者不可混用。查询结果的`rerank`记录状态、模型、候选数、处理数、耗时及回退原因。管理检索、客服和Spring AI检索适配器共用此入口。

配置：

```dotenv
RAG_B_RERANK_ENABLED=true
RAG_B_RERANK_MODEL=bge-reranker-v2-m3
RAG_B_RERANK_MAX_CANDIDATES=80
RAG_B_RERANK_BATCH_SIZE=20
RAG_B_RERANK_TIMEOUT_MILLIS=5000
```

默认开关为false；本地`.env.local`可启用。模型地址和Key为空时复用`AI_EMBEDDING_ENDPOINT`、`AI_EMBEDDING_KEY`；独立地址使用独立Key，避免把旧凭据发送到不同主机。可用`RAG_B_RERANK_ENDPOINT`、`RAG_B_RERANK_API_KEY`单独配置。Key不进入日志、报告或提示文本。

本地环境更新后通过`docker compose --env-file .env.local -f compose.local.yml up -d --no-deps backend`应用。关闭`RAG_B_RERANK_ENABLED`并重启即可回到旧排序，不需要回滚索引。

首次24题验收因超过供应商单次文档数而未通过，49次请求及原始代码、日志完整封存在attempt-1，其中26次本地查询、22次重排和1次接口限制探测，不重用旧额度。修正后的验收选取8道有代表性的原题，最多50次推理请求，Embedding与每个Reranker批次均计入新账本，无聊天、入库、重建或索引切换。两道额外禁用开关查询与旧基线核对。定时任务和恢复任务在测试进程中关闭；本轮是接入回归，不冒充完整24题新质量评测。

命令：`python scripts/rag_b_reranker_integration.py freeze`、`run`、`verify`。

旧实验的源文件散列指向接入前代码，现已归档至`history-sources`。旧报告和账本保持原样；用`python scripts/verify_rag_b_reranker_history.py`在临时目录恢复旧输入后执行旧验证程序，避免将源代码演进误判为旧结果被改写。
