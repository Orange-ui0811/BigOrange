package com.orangejd.server.service.impl;

import com.orangejd.pojo.entity.KnowledgeDocument;
import com.orangejd.server.config.AiEmbeddingProperties;
import com.orangejd.server.mapper.KnowledgeDocumentMapper;
import com.orangejd.server.service.ProjectKnowledgeSyncService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class KnowledgeReindexService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeReindexService.class);
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 3;

    private final KnowledgeDocumentMapper mapper;
    private final DocumentServiceImpl documentService;
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final AiEmbeddingProperties embeddingProperties;
    private final RedisVectorStoreProperties redisProperties;
    private final ProjectKnowledgeSyncService projectKnowledgeSyncService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor;

    private volatile ReindexStatus current = ReindexStatus.idle();

    @Autowired
    public KnowledgeReindexService(KnowledgeDocumentMapper mapper,
                                   DocumentServiceImpl documentService,
                                   VectorStore vectorStore,
                                   EmbeddingModel embeddingModel,
                                   AiEmbeddingProperties embeddingProperties,
                                   RedisVectorStoreProperties redisProperties,
                                   ProjectKnowledgeSyncService projectKnowledgeSyncService) {
        this.mapper = mapper;
        this.documentService = documentService;
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.embeddingProperties = embeddingProperties;
        this.redisProperties = redisProperties;
        this.projectKnowledgeSyncService = projectKnowledgeSyncService;
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "knowledge-reindex");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newSingleThreadExecutor(factory);
    }

    public ReindexStatus start() {
        validateConfiguration();
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A knowledge reindex is already running");
        }
        current = new ReindexStatus("QUEUED", 0, 0, 0, 0, 0, 0,
                Instant.now().toString(), null, "Queued");
        executor.submit(this::runReindex);
        return current;
    }

    public ReindexStatus status() {
        return current;
    }

    private void validateConfiguration() {
        if (embeddingProperties.getApiKey() == null || embeddingProperties.getApiKey().isBlank()) {
            throw new IllegalStateException("AI_EMBEDDING_KEY is not configured");
        }
        if (!(vectorStore instanceof RedisVectorStore)) {
            throw new IllegalStateException("Redis VectorStore is not available");
        }
        if (redisProperties.getPrefix() == null || redisProperties.getPrefix().isBlank()) {
            throw new IllegalStateException("Redis vector prefix is empty");
        }
    }

    private void runReindex() {
        String startedAt = Instant.now().toString();
        try {
            ProjectKnowledgeSyncService.SyncSummary projectSync = projectKnowledgeSyncService.syncAllPublished();
            if (projectSync.failed() > 0) {
                throw new IllegalStateException("项目知识同步失败 " + projectSync.failed() + " 条");
            }
            List<KnowledgeDocument> entities = mapper.selectAllWithContent();
            current = new ReindexStatus("CHECKING", entities.size(), 0, 0, 0, 0, 0,
                    startedAt, null, "Checking embedding service");

            float[] probe = embeddingModel.embed("OrangeVC knowledge reindex connectivity check");
            if (probe == null || probe.length == 0) {
                throw new IllegalStateException("Embedding service returned an empty vector");
            }

            long deletedKeys = clearVectorDocuments();
            current = new ReindexStatus("RUNNING", entities.size(), 0, 0, 0, 0, deletedKeys,
                    startedAt, null, "Rebuilding vectors");

            List<Document> batch = new ArrayList<>(BATCH_SIZE);
            int processedDocuments = 0;
            int skippedDocuments = 0;
            int totalChunks = 0;
            int indexedChunks = 0;

            for (KnowledgeDocument entity : entities) {
                if (entity.getContent() == null || entity.getContent().isBlank()) {
                    skippedDocuments++;
                    processedDocuments++;
                    current = progress(startedAt, entities.size(), processedDocuments, totalChunks,
                            indexedChunks, skippedDocuments, deletedKeys, "Skipped document without content");
                    continue;
                }

                List<Document> docs = documentService.buildVectorDocuments(entity);
                totalChunks += docs.size();
                for (Document doc : docs) {
                    batch.add(doc);
                    if (batch.size() >= BATCH_SIZE) {
                        addWithRetry(batch);
                        indexedChunks += batch.size();
                        batch.clear();
                        current = progress(startedAt, entities.size(), processedDocuments, totalChunks,
                                indexedChunks, skippedDocuments, deletedKeys, "Rebuilding vectors");
                    }
                }
                processedDocuments++;
                current = progress(startedAt, entities.size(), processedDocuments, totalChunks,
                        indexedChunks, skippedDocuments, deletedKeys, "Rebuilding vectors");
            }

            if (!batch.isEmpty()) {
                addWithRetry(batch);
                indexedChunks += batch.size();
            }

            current = new ReindexStatus("COMPLETED", entities.size(), processedDocuments, totalChunks,
                    indexedChunks, skippedDocuments, deletedKeys, startedAt, Instant.now().toString(),
                    "Knowledge vectors rebuilt");
            log.info("[RAG] Reindex completed: documents={}, chunks={}, skipped={}, deletedKeys={}",
                    processedDocuments, indexedChunks, skippedDocuments, deletedKeys);
        } catch (Exception e) {
            log.error("[RAG] Reindex failed", e);
            ReindexStatus previous = current;
            current = new ReindexStatus("FAILED", previous.totalDocuments(), previous.processedDocuments(),
                    previous.totalChunks(), previous.indexedChunks(), previous.skippedDocuments(),
                    previous.deletedKeys(), startedAt, Instant.now().toString(), safeMessage(e));
        } finally {
            running.set(false);
        }
    }

    private ReindexStatus progress(String startedAt, int totalDocuments, int processedDocuments,
                                   int totalChunks, int indexedChunks, int skippedDocuments,
                                   long deletedKeys, String message) {
        return new ReindexStatus("RUNNING", totalDocuments, processedDocuments, totalChunks, indexedChunks,
                skippedDocuments, deletedKeys, startedAt, null, message);
    }

    private long clearVectorDocuments() {
        RedisVectorStore redisStore = (RedisVectorStore) vectorStore;
        JedisPooled jedis = redisStore.getJedis();
        String prefix = redisProperties.getPrefix();
        ScanParams params = new ScanParams().match(prefix + "*").count(500);
        String cursor = ScanParams.SCAN_POINTER_START;
        List<String> keys = new ArrayList<>();
        do {
            ScanResult<String> result = jedis.scan(cursor, params);
            keys.addAll(result.getResult());
            cursor = result.getCursor();
        } while (!ScanParams.SCAN_POINTER_START.equals(cursor));

        for (int offset = 0; offset < keys.size(); offset += 500) {
            int end = Math.min(offset + 500, keys.size());
            jedis.del(keys.subList(offset, end).toArray(String[]::new));
        }
        return keys.size();
    }

    private void addWithRetry(List<Document> documents) throws InterruptedException {
        RuntimeException last = null;
        List<Document> immutableBatch = List.copyOf(documents);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                vectorStore.add(immutableBatch);
                return;
            } catch (RuntimeException e) {
                last = e;
                log.warn("[RAG] Reindex batch failed, attempt {}/{}: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) Thread.sleep(1000L * attempt);
            }
        }
        throw last;
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public record ReindexStatus(String state, int totalDocuments, int processedDocuments,
                                int totalChunks, int indexedChunks, int skippedDocuments,
                                long deletedKeys, String startedAt, String finishedAt, String message) {
        static ReindexStatus idle() {
            return new ReindexStatus("IDLE", 0, 0, 0, 0, 0, 0, null, null, "Not started");
        }
    }
}
