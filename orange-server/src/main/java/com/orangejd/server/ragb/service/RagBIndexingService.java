package com.orangejd.server.ragb.service;

import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.domain.RagBChunk;
import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.ragb.domain.RagBDocumentVersion;
import com.orangejd.server.ragb.domain.RagBIndexGeneration;
import com.orangejd.server.ragb.mapper.RagBMapper;
import com.orangejd.server.ragb.retrieval.RagBIndexRouter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

@Service
public class RagBIndexingService {
    private final RagBProperties properties;
    private final RagBMapper mapper;
    private final RagBIndexRouter indexes;
    private final TransactionTemplate transactions;

    public RagBIndexingService(RagBProperties properties, RagBMapper mapper,
                               RagBIndexRouter indexes, TransactionTemplate transactions) {
        this.properties = properties;
        this.mapper = mapper;
        this.indexes = indexes;
        this.transactions = transactions;
    }

    public Map<String, Object> index(Long documentId, Long versionId) {
        if (!properties.isEnabled()) throw new IllegalStateException("B 版 RAG 实验模块未启用");
        RagBDocument document = mapper.selectDocument(documentId);
        RagBDocumentVersion version = mapper.selectVersion(versionId);
        if (document == null || version == null || !documentId.equals(version.getDocumentId())) {
            throw new IllegalArgumentException("B 版文档或版本不存在");
        }
        if ("DELETED".equals(document.getStatus())) return Map.of("skipped", true, "reason", "DOCUMENT_DELETED");
        if ("READY".equals(version.getStatus()) && versionId.equals(document.getActiveVersionId())) {
            return Map.of("skipped", true, "reason", "ALREADY_PUBLISHED");
        }
        try {
            // MySQL reports two affected rows for this multi-table UPDATE when both document and version change.
            // Treat any positive count as success; requiring exactly one made every fresh import fail.
            if (mapper.markProcessing(documentId, versionId) <= 0) {
                throw new IllegalStateException("文档状态不允许处理");
            }
            RagBIndexGeneration generation = mapper.selectActiveGeneration();
            if (generation == null) throw new IllegalStateException("没有可用的 B 版索引代际");
            List<RagBChunk> chunks = mapper.selectChunks(versionId);
            if (chunks.size() != version.getChunkCount()) {
                throw new IllegalStateException("分块记录数量与版本声明不一致");
            }
            Map<Long, String> keys = indexes.index(document, generation, chunks);
            for (RagBChunk chunk : chunks) {
                String key = keys.get(chunk.getId());
                if (key == null || mapper.markChunkIndexed(chunk.getId(), versionId, key) != 1) {
                    throw new IllegalStateException("分块向量状态写入失败: " + chunk.getId());
                }
                mapper.upsertGenerationChunk(generation.getId(), chunk.getId(), key);
            }
            transactions.executeWithoutResult(status -> {
                if (mapper.markVersionReady(documentId, versionId, generation.getId()) != 1
                        || mapper.publishVersion(documentId, versionId) != 1) {
                    throw new IllegalStateException("发布 B 版文档版本失败");
                }
            });
            return Map.of("documentId", documentId, "versionId", versionId,
                    "generation", generation.getGenerationKey(), "adapter", indexes.adapterType(generation),
                    "indexedChunks", chunks.size());
        } catch (RuntimeException ex) {
            throw fail(documentId, versionId, ex);
        }
    }

    private RuntimeException fail(Long documentId, Long versionId, RuntimeException failure) {
        mapper.markFailed(documentId, versionId, failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        return failure;
    }
}
