package com.orangejd.server.ragb.service;

import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.foundation.task.SystemTaskService;
import com.orangejd.pojo.entity.KnowledgeDocument;
import com.orangejd.server.mapper.KnowledgeDocumentMapper;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.domain.RagBChunk;
import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.ragb.domain.RagBDocumentVersion;
import com.orangejd.server.ragb.domain.RagBIndexGeneration;
import com.orangejd.server.ragb.ingest.RagBChunker;
import com.orangejd.server.ragb.ingest.RagBMetadataExtractor;
import com.orangejd.server.ragb.ingest.RagBBusinessMetadataProvider;
import com.orangejd.server.ragb.mapper.RagBMapper;
import com.orangejd.server.ragb.retrieval.RagBVectorRepository;
import com.orangejd.server.ragb.task.RagBIndexTask;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RagBDocumentService {
    private final RagBDocumentInputs inputs;
    private final RagBProperties properties;
    private final RagBChunker chunker;
    private final RagBMapper mapper;
    private final ObjectMapper objectMapper;
    private final SystemTaskService taskService;
    private final RagBVectorRepository vectorRepository;
    private final KnowledgeDocumentMapper legacyDocuments;
    private final RagBMetadataExtractor metadataExtractor;
    private final RagBBusinessMetadataProvider businessMetadata;

    public RagBDocumentService(RagBProperties properties, RagBChunker chunker, RagBMapper mapper,
                               ObjectMapper objectMapper, SystemTaskService taskService,
                               RagBVectorRepository vectorRepository, KnowledgeDocumentMapper legacyDocuments,
                               RagBMetadataExtractor metadataExtractor,
                               RagBBusinessMetadataProvider businessMetadata) {
        this.inputs = new RagBDocumentInputs(properties, objectMapper);
        this.properties = properties;
        this.chunker = chunker;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.taskService = taskService;
        this.vectorRepository = vectorRepository;
        this.legacyDocuments = legacyDocuments;
        this.metadataExtractor = metadataExtractor;
        this.businessMetadata = businessMetadata;
    }

    @Transactional
    public Map<String, Object> upload(MultipartFile file, String requestedTitle) {
        requireEnabled();
        byte[] bytes = inputs.readAndValidate(file);
        String content = inputs.decodeUtf8(bytes).replace("\r\n", "\n").replace('\r', '\n');
        if (content.isBlank()) throw new IllegalArgumentException("文档内容不能为空");
        String title = inputs.sanitizeTitle(requestedTitle == null || requestedTitle.isBlank()
                ? file.getOriginalFilename() : requestedTitle);

        RagBDocument document = new RagBDocument();
        document.setSourceType("MANUAL");
        document.setSourceRef("manual:" + UUID.randomUUID());
        document.setTitle(title);
        document.setStatus("PENDING");
        document.setContentHash(inputs.sha256(content));
        mapper.insertDocument(document);

        RagBDocumentVersion version = createVersion(document, content, 1);
        Long taskId = enqueue(document.getId(), version.getId(), 0);
        return summary(document, version, taskId);
    }

    public List<RagBDocument> list(int limit) {
        return mapper.selectDocuments(Math.max(1, Math.min(limit, 200)));
    }

    @Transactional
    public Map<String, Object> importLegacy(Long legacyDocumentId) {
        requireEnabled();
        KnowledgeDocument source = legacyDocuments.selectById(legacyDocumentId);
        if (source == null) throw new IllegalArgumentException("原知识库文档不存在");
        String content = source.getOriginalContent();
        if (content == null || content.isBlank()) content = source.getContent();
        if (content == null || content.isBlank()) throw new IllegalArgumentException("原知识库文档没有可导入的内容");
        content = content.replace("\r\n", "\n").replace('\r', '\n');
        if (content.getBytes(StandardCharsets.UTF_8).length > properties.getMaxFileBytes()) {
            throw new IllegalArgumentException("原知识库文档超过 B 版单文档上限，未进行截断导入");
        }
        String sourceType = inputs.normalizeSource(source.getSource());
        String sourceRef = source.getSourceRef();
        if (sourceRef == null || sourceRef.isBlank()) sourceRef = "legacy-document:" + legacyDocumentId;
        Map<String, Object> result = new LinkedHashMap<>(importSource(sourceType, sourceRef, source.getTitle(), content));
        result.put("legacyDocumentId", legacyDocumentId);
        return result;
    }

    @Transactional
    public Map<String, Object> importSource(String sourceType, String sourceRef, String title, String suppliedContent) {
        requireEnabled();
        sourceType = inputs.normalizeSource(sourceType);
        if (sourceRef == null || sourceRef.isBlank()) throw new IllegalArgumentException("资料来源标识不能为空");
        String content = suppliedContent == null ? "" : suppliedContent.replace("\r\n", "\n").replace('\r', '\n');
        if (content.isBlank()) throw new IllegalArgumentException("资料内容不能为空");
        if (content.getBytes(StandardCharsets.UTF_8).length > properties.getMaxFileBytes()) {
            throw new IllegalArgumentException("资料超过 B 版单文档上限，未进行截断导入");
        }
        String safeTitle = inputs.sanitizeTitle(title);
        String hash = inputs.sha256(content);
        RagBDocument document = mapper.selectDocumentBySource(sourceType, sourceRef);
        if (document != null && hash.equals(document.getContentHash()) && safeTitle.equals(document.getTitle()) && "READY".equals(document.getStatus())) {
            RagBDocumentVersion latest = mapper.selectLatestVersion(document.getId());
            if (latest != null && "READY".equals(latest.getStatus())) {
                return Map.of("documentId", document.getId(), "status", "UNCHANGED", "sourceType", sourceType,
                        "sourceRef", sourceRef);
            }
        }
        if (document == null) {
            document = new RagBDocument();
            document.setSourceType(sourceType);
            document.setSourceRef(sourceRef);
            document.setTitle(safeTitle);
            document.setStatus("PENDING");
            document.setContentHash(hash);
            mapper.insertDocument(document);
        } else {
            mapper.observeSourceUpdate(document.getId(), safeTitle, hash);
            document = mapper.selectDocument(document.getId());
        }
        RagBDocumentVersion version = createVersion(document, content, mapper.selectMaxVersionNo(document.getId()) + 1);
        Long taskId = enqueue(document.getId(), version.getId(), 0);
        Map<String, Object> result = new LinkedHashMap<>(summary(document, version, taskId));
        result.put("sourceType", sourceType);
        result.put("sourceRef", sourceRef);
        return result;
    }

    public Map<String, Object> detail(Long documentId) {
        RagBDocument document = requireDocument(documentId);
        RagBDocumentVersion version = document.getActiveVersionId() == null
                ? mapper.selectLatestVersion(documentId) : mapper.selectVersion(document.getActiveVersionId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("document", document);
        result.put("version", version);
        result.put("chunks", version == null ? List.of() : mapper.selectChunks(version.getId()));
        return result;
    }

    @Transactional
    public Map<String, Object> refreshMetadata() {
        requireEnabled();
        int documents = 0;
        int chunks = 0;
        for (RagBDocument document : mapper.selectReadyDocuments()) {
            RagBDocumentVersion version = mapper.selectVersion(document.getActiveVersionId());
            if (version == null || version.getOriginalContent() == null) continue;
            Map<String, Object> values = metadataExtractor.extract(document, version.getOriginalContent(),
                    businessMetadata.metadata(document));
            for (RagBChunk chunk : mapper.selectChunks(version.getId())) {
                mapper.updateChunkMetadata(chunk.getId(), inputs.metadata(document,
                        new RagBChunker.ChunkDraft(chunk.getChunkIndex(), chunk.getSectionPath(),
                                chunk.getStartOffset(), chunk.getEndOffset(), chunk.getContent()), values));
                chunks++;
            }
            documents++;
        }
        return Map.of("documents", documents, "chunks", chunks, "requiresIndexRebuild", true);
    }

    @Transactional
    public Map<String, Object> retry(Long documentId) {
        requireEnabled();
        RagBDocument document = requireDocument(documentId);
        RagBDocumentVersion version = mapper.selectLatestVersion(documentId);
        if (version == null) throw new IllegalStateException("文档版本不存在");
        if (!"FAILED".equals(version.getStatus())) throw new IllegalArgumentException("只有失败版本可以重试");
        if (document.getActiveVersionId() == null) {
            if (mapper.prepareRetry(documentId) != 1) throw new IllegalStateException("文档状态已变化，请刷新后重试");
        }
        if (mapper.prepareVersionRetry(documentId, version.getId()) != 1) {
            throw new IllegalStateException("版本状态已变化，请刷新后重试");
        }
        RagBDocument updated = mapper.selectDocument(documentId);
        Long taskId = enqueue(documentId, version.getId(), updated.getRetryCount());
        return summary(updated, version, taskId);
    }

    @Transactional
    public Map<String, Object> delete(Long documentId) {
        requireEnabled();
        requireDocument(documentId);
        List<String> vectorKeys = mapper.selectAllGenerationVectorKeys(documentId);
        if (vectorKeys.isEmpty()) vectorKeys = mapper.selectVectorKeys(documentId);
        mapper.tombstone(documentId);
        mapper.tombstoneChunks(documentId);
        boolean cleaned = true;
        String cleanupError = null;
        try {
            vectorRepository.deleteOwned(vectorKeys);
        } catch (RuntimeException ex) {
            cleaned = false;
            cleanupError = inputs.abbreviate(ex.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", documentId);
        result.put("excludedFromRetrieval", true);
        result.put("vectorCleanupCompleted", cleaned);
        if (cleanupError != null) result.put("cleanupError", cleanupError);
        return result;
    }

    private Long enqueue(Long documentId, Long versionId, int retry) {
        return taskService.enqueue(RagBIndexTask.TASK_TYPE,
                "document:" + documentId + ":version:" + versionId + ":retry:" + retry,
                Map.of("documentId", documentId, "versionId", versionId), 3);
    }

    @Transactional
    public Long prepareGenerationVersion(RagBDocument document, RagBIndexGeneration generation) {
        Long existing = mapper.selectGenerationVersion(generation.getId(), document.getId());
        RagBDocumentVersion current = mapper.selectVersion(document.getActiveVersionId());
        if (current == null) throw new IllegalStateException("重建文档缺少有效原文版本");
        if (existing != null) {
            RagBDocumentVersion prepared=mapper.selectVersion(existing);
            if(prepared==null || !current.getContentHash().equals(prepared.getContentHash())) {
                throw new IllegalStateException("构建快照与当前原文不一致，必须创建新代际");
            }
            return existing;
        }
        RagBDocumentVersion version = createVersion(document, current.getOriginalContent(),
                mapper.selectMaxVersionNo(document.getId()) + 1, generation.getChunkPolicy());
        mapper.insertGenerationDocument(generation.getId(), document.getId(), version.getId());
        return version.getId();
    }

    private RagBDocumentVersion createVersion(RagBDocument document, String content, int versionNo) {
        RagBIndexGeneration active = mapper.selectActiveGeneration();
        String policy = active == null || active.getChunkPolicy() == null ? properties.getChunkPolicy() : active.getChunkPolicy();
        return createVersion(document, content, versionNo, policy);
    }

    private RagBDocumentVersion createVersion(RagBDocument document, String content, int versionNo, String policy) {
        List<RagBChunker.ChunkDraft> drafts = chunker.split(content, policy);
        Map<String, Object> documentMetadata = metadataExtractor.extract(document, content,
                businessMetadata.metadata(document));
        RagBDocumentVersion version = new RagBDocumentVersion();
        version.setDocumentId(document.getId());
        version.setVersionNo(versionNo);
        version.setContentHash(inputs.sha256(content));
        version.setOriginalContent(content);
        version.setStatus("PENDING");
        version.setChunkCount(drafts.size());
        mapper.insertVersion(version);
        for (RagBChunker.ChunkDraft draft : drafts) {
            RagBChunk chunk = new RagBChunk();
            chunk.setDocumentId(document.getId());
            chunk.setVersionId(version.getId());
            chunk.setChunkIndex(draft.index());
            chunk.setSectionPath(draft.sectionPath());
            chunk.setStartOffset(draft.startOffset());
            chunk.setEndOffset(draft.endOffset());
            chunk.setContent(draft.content());
            chunk.setMetadataJson(inputs.metadata(document, draft, documentMetadata));
            chunk.setIndexStatus("PENDING");
            mapper.insertChunk(chunk);
        }
        return version;
    }

    private RagBDocument requireDocument(Long id) {
        RagBDocument document = mapper.selectDocument(id);
        if (document == null) throw new IllegalArgumentException("B 版文档不存在");
        return document;
    }

    private Map<String, Object> summary(RagBDocument document, RagBDocumentVersion version, Long taskId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", document.getId());
        result.put("versionId", version.getId());
        result.put("status", document.getStatus());
        result.put("chunkCount", version.getChunkCount());
        result.put("taskId", taskId);
        return result;
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw new IllegalStateException("B 版 RAG 实验模块未启用");
    }
}
