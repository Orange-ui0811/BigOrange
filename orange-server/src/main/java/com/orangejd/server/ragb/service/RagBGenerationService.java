package com.orangejd.server.ragb.service;

import com.orangejd.server.foundation.task.SystemTaskService;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.domain.RagBChunk;
import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.ragb.domain.RagBIndexGeneration;
import com.orangejd.server.ragb.mapper.RagBMapper;
import com.orangejd.server.ragb.retrieval.RagBIndexRouter;
import com.orangejd.server.ragb.task.RagBRebuildTask;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RagBGenerationService {
    private final RagBProperties properties;
    private final RagBMapper mapper;
    private final RagBIndexRouter indexes;
    private final SystemTaskService tasks;
    private final TransactionTemplate transactions;
    private final RagBDocumentService documents;

    public RagBGenerationService(RagBProperties properties, RagBMapper mapper, RagBIndexRouter indexes,
                                 SystemTaskService tasks, TransactionTemplate transactions) {
        this(properties,mapper,indexes,tasks,transactions,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public RagBGenerationService(RagBProperties properties, RagBMapper mapper, RagBIndexRouter indexes,
                                 SystemTaskService tasks, TransactionTemplate transactions,RagBDocumentService documents) {
        this.documents=documents;
        this.properties = properties;
        this.mapper = mapper;
        this.indexes = indexes;
        this.tasks = tasks;
        this.transactions = transactions;
    }

    @Transactional
    public Map<String, Object> startRebuild() { return startRebuild("HNSW_HYBRID"); }

    @Transactional
    public Map<String, Object> startRebuild(String requestedAdapter) {
        requireEnabled();
        String adapter = normalizeAdapter(requestedAdapter);
        if (mapper.countBuildingGenerations() > 0) throw new IllegalStateException("已有 B 版索引正在构建");
        String key = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        RagBIndexGeneration generation = new RagBIndexGeneration();
        generation.setGenerationKey(key);
        generation.setRedisPrefix(properties.getRedisPrefix() + "g:" + key + ":");
        generation.setAdapterType(adapter);
        if (!"bge-m3".equals(properties.getEmbeddingModel())) throw new IllegalStateException("新的 B 版代际必须使用 bge-m3");
        generation.setEmbeddingModel("bge-m3");
        generation.setEmbeddingProvider(properties.getEmbeddingProvider());
        generation.setEmbeddingRevision(properties.getEmbeddingRevision());
        generation.setEmbeddingDimensions(1024);
        generation.setChunkPolicy(properties.getChunkPolicy());
        generation.setStatus("BUILDING");
        mapper.insertGeneration(generation);
        Long taskId = tasks.enqueue(RagBRebuildTask.TASK_TYPE, "generation:" + generation.getId(),
                Map.of("generationId", generation.getId()), 1);
        return Map.of("generationId", generation.getId(), "generationKey", key,
                "adapter", adapter, "status", "BUILDING", "taskId", taskId);
    }

    public Map<String, Object> rebuild(Long generationId) {
        requireEnabled();
        RagBIndexGeneration generation = mapper.selectGeneration(generationId);
        if (generation == null || Boolean.TRUE.equals(generation.getActive())) {
            throw new IllegalArgumentException("待构建索引代际不存在");
        }
        mapper.markGenerationBuilding(generationId);
        List<RagBDocument> snapshot = mapper.selectReadyDocuments();
        String fingerprint = fingerprint(snapshot);
        int expectedChunks = 0;
        try {
            if (snapshot.isEmpty()) throw new IllegalStateException("没有可发布的 B 版文档，已保留当前索引");
            for (RagBDocument document : snapshot) {
                Long versionId = generation.getChunkPolicy() == null || "CHAR_500_V1".equals(generation.getChunkPolicy())
                        ? document.getActiveVersionId() : documents.prepareGenerationVersion(document, generation);
                List<RagBChunk> chunks = mapper.selectChunks(versionId);
                expectedChunks += chunks.size();
                Map<Long, String> keys = indexes.index(document, generation, chunks);
                for (RagBChunk chunk : chunks) {
                    String vectorKey = keys.get(chunk.getId());
                    if (vectorKey == null) throw new IllegalStateException("新代际缺少分块向量: " + chunk.getId());
                    mapper.upsertGenerationChunk(generationId, chunk.getId(), vectorKey);
                }
            }
            if (!fingerprint.equals(fingerprint(mapper.selectReadyDocuments()))) {
                throw new IllegalStateException("构建期间有效文档发生变化，已拒绝切换并保留上一版索引");
            }
            int actualChunks = mapper.countGenerationChunks(generationId);
            if (actualChunks != expectedChunks) {
                throw new IllegalStateException("新索引校验失败，预期 " + expectedChunks + " 个分块，实际 " + actualChunks + " 个");
            }
            if (mapper.markGenerationReady(generationId, snapshot.size(), actualChunks) != 1) {
                throw new IllegalStateException("新索引无法进入待发布状态");
            }
            transactions.executeWithoutResult(status -> {
                mapper.retireActiveGeneration();
                if (mapper.activateGeneration(generationId) != 1) throw new IllegalStateException("新索引切换失败");
                mapper.publishGenerationChunkKeys(generationId);
                if (generation.getChunkPolicy() != null && !"CHAR_500_V1".equals(generation.getChunkPolicy())) {
                    mapper.publishGenerationVersions(generationId);
                }
            });
            return Map.of("generationId", generationId, "status", "ACTIVE",
                    "adapter", indexes.adapterType(generation), "documents", snapshot.size(), "chunks", actualChunks);
        } catch (RuntimeException ex) {
            mapper.markGenerationFailed(generationId, message(ex));
            throw ex;
        }
    }

    public List<RagBIndexGeneration> list(int limit) {
        return mapper.selectGenerations(Math.max(1, Math.min(limit, 50)));
    }

    public Map<String, Object> activeConfiguration() {
        RagBIndexGeneration active=mapper.selectActiveGeneration();
        if(active==null) return Map.of("status","UNAVAILABLE");
        return Map.of("generation",active.getGenerationKey(),"embeddingModel",active.getEmbeddingModel(),
                "dimensions",active.getEmbeddingDimensions(),"chunkPolicy",active.getChunkPolicy(),
                "embeddingProvider", active.getEmbeddingProvider() == null ? "GITEE" : active.getEmbeddingProvider(),
                "embeddingRevision", active.getEmbeddingRevision() == null ? "UNSPECIFIED" : active.getEmbeddingRevision());
    }

    private String fingerprint(List<RagBDocument> documents) {
        StringBuilder value = new StringBuilder();
        for (RagBDocument document : documents) {
            value.append(document.getId()).append(':').append(document.getActiveVersionId()).append(';');
        }
        return value.toString();
    }

    private String message(RuntimeException ex) {
        String value = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return value.length() <= 1900 ? value : value.substring(0, 1900);
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw new IllegalStateException("B 版 RAG 实验模块未启用");
    }

    private String normalizeAdapter(String requested) {
        String adapter = requested == null ? "HNSW_HYBRID" : requested.trim().toUpperCase();
        if (!Set.of("EXACT", "HNSW_HYBRID").contains(adapter)) {
            throw new IllegalArgumentException("索引适配器仅支持 EXACT 或 HNSW_HYBRID");
        }
        return adapter;
    }
}
