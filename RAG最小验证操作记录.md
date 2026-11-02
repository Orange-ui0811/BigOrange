# 第一个最小 RAG 验证

## 启动方式

在 PowerShell 进入 `D:\Orangejd`，执行：

    docker compose -f compose.local.yml up -d mysql redis
    docker compose -f compose.local.yml ps

确认 MySQL 和 Redis 为 running/healthy 后执行：

    docker compose -f compose.local.yml up backend

后端地址是 `http://localhost:18080`。第一次启动会下载 Maven 依赖并编译。

配置 Embedding Key 时，在 `D:\Orangejd\.env` 增加 `AI_EMBEDDING_KEY=你的Gitee向量模型Key`，不要把真实 Key 提交到 Git。

## 健康检查

打开 `http://localhost:18080/health/detail`，确认 `rag_vector_store` 为 `UP`。这只能证明 Redis VectorStore 已连接。

## 固定测试知识

仓库根目录的 `rag-verification-fixture.md` 是本次实验的固定知识样例：项目 ID `RAG-DEMO-JAVA-001`，标题“Java 入门任务看板”，目录“初级项目”，难度“初级”，周期“两周”，技术栈“Java、Spring Boot、MySQL”。优先通过平台现有的初级项目发布/同步流程导入真实项目；样例文件只用于本地验证切片和检索。

## 测试问题

有没有适合 Java 初学者、两周内可以完成的项目？

检索前先记下预期项目 ID：`RAG-DEMO-JAVA-001`。

## 执行与记录

当前检索逻辑主要集中在 `TrendContextBuilder`，`TrendRedisMetadata` 负责词法检索和 metadata 恢复。若当前分支没有通用检索接口，在本地增加临时调试测试，调用 `VectorStore.similaritySearch(...)` 或 `TrendContextBuilder` 公开方法，输出文档文本、metadata、score 和耗时。同一问题执行 3 次。

| 测试问题 | 召回文档 | 文档来源 | 相似度 | 项目 ID | 检索耗时 | 是否返回正确项目 |
| --- | --- | --- | ---: | --- | ---: | --- |
| 有没有适合 Java 初学者、两周内可以完成的项目？ | 待填写：标题/片段摘要 | 待填写：`source`、`catalog`、`sourceRef`、semantic/lexical | 待填写 | 待填写 | 待填写 ms | 是/否 |

## 判断规则

- 召回文档填写标题、摘要或 chunk 序号。
- 文档来源记录 metadata 的 `source`、`sourceRef`、`catalog`，并注明 semantic 或 lexical。
- 没有语义分数时填写 `N/A`，不要伪造数值。
- 项目 ID 必须能回查项目表。
- 检索耗时从发起检索到拿到最后文档，不包含大模型生成时间。
- 只有来源属于项目知识、项目 ID 正确、内容符合问题约束时，才填“是”。

## 通过标准

健康检查为 UP；返回非空片段；有可追溯 metadata；有正确 `projectId`；连续 3 次至少 2 次返回正确项目；耗时可记录。只有健康检查通过时，结论写“VectorStore 可用，RAG 尚未验证”。

## 排查顺序

1. VectorStore 非 UP：检查 Redis 地址、端口和向量索引。
2. UP 但无文档：检查发布、同步、Embedding 和 `knowledge_document`。
3. `projectId` 为空：检查 `ProjectKnowledgeSyncService` 的 metadata 写入。
4. 召回不相关：先加项目状态或 `catalog` 过滤，再评估 chunk 和 embedding。
