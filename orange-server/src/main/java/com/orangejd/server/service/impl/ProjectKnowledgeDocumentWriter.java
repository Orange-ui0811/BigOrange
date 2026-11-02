package com.orangejd.server.service.impl;

import com.orangejd.pojo.entity.KnowledgeDocument;
import com.orangejd.pojo.vo.KnowledgeDocumentVO;
import com.orangejd.server.mapper.KnowledgeDocumentMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProjectKnowledgeDocumentWriter {
    private static final Set<String> SOURCES = Set.of("BEGINNER_PROJECT", "CAREER_PROJECT");
    private static final int CHUNK_SIZE = 500;
    private static final int OVERLAP = 50;
    private static final int MAX_CHUNKS = 100;
    private final KnowledgeDocumentMapper mapper;
    private final VectorStore vectorStore;

    public ProjectKnowledgeDocumentWriter(KnowledgeDocumentMapper mapper,
                                          @org.springframework.lang.Nullable VectorStore vectorStore) {
        this.mapper = mapper;
        this.vectorStore = vectorStore;
    }

    public KnowledgeDocumentVO upsert(String source, String sourceRef, String title, String content) {
        validate(source, sourceRef, content);
        KnowledgeDocument entity = mapper.selectBySourceRef(sourceRef);
        List<String> chunks = chunks(content);
        if (entity == null) {
            entity = new KnowledgeDocument();
            entity.setSourceRef(sourceRef);
            entity.setOriginalContent(content);
            configure(entity, source, title, content, chunks.size());
            mapper.insert(entity);
        } else {
            entity.setOriginalContent(content);
            configure(entity, source, title, content, chunks.size());
            mapper.updatePipeline(entity);
        }
        try {
            if (vectorStore == null) throw new IllegalStateException("VectorStore 不可用");
            vectorStore.delete("docId == '" + entity.getId() + "'");
            vectorStore.add(vectorDocuments(entity, chunks));
            entity.setVectorStatus("DONE");
            mapper.updatePipeline(entity);
            return view(entity);
        } catch (RuntimeException error) {
            entity.setVectorStatus("FAILED");
            entity.setPipelineError(error(error));
            mapper.updatePipeline(entity);
            throw error;
        }
    }

    public void delete(String sourceRef) {
        KnowledgeDocument entity = mapper.selectBySourceRef(sourceRef);
        if (entity == null) return;
        if (vectorStore != null) vectorStore.delete("docId == '" + entity.getId() + "'");
        mapper.deleteById(entity.getId());
    }

    static void enrichMetadata(Map<String, Object> metadata, KnowledgeDocument entity) {
        metadata.put("sourceRef", entity.getSourceRef() == null ? "" : entity.getSourceRef());
        if (!SOURCES.contains(entity.getSource())) return;
        metadata.put("catalog", entity.getSource());
        String ref = entity.getSourceRef();
        int separator = ref == null ? -1 : ref.indexOf(':');
        if (separator >= 0 && separator + 1 < ref.length()) metadata.put("projectId", ref.substring(separator + 1));
    }

    private void configure(KnowledgeDocument entity, String source, String title,
                           String content, int chunkCount) {
        entity.setSource(source);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setFileSize((long) content.length());
        entity.setChunkCount(chunkCount);
        entity.setFetchStatus("DONE");
        entity.setTranslationStatus("DONE");
        entity.setStorageStatus("DONE");
        entity.setVectorStatus("PENDING");
        entity.setPipelineError(null);
    }

    private List<String> chunks(String content) {
        List<String> values = new ArrayList<>();
        int start = 0;
        while (start < content.length() && values.size() < MAX_CHUNKS) {
            int end = Math.min(start + CHUNK_SIZE, content.length());
            if (end < content.length()) {
                int newline = content.lastIndexOf('\n', end);
                if (newline > start + CHUNK_SIZE / 2) end = newline;
            }
            String chunk = content.substring(start, end).trim();
            String plain = chunk.replaceAll("\\s+", "").replaceAll("[#*\\-`>|]+", "");
            if (plain.length() >= 10) values.add(chunk.length() > 400 ? chunk.substring(0, 400) : chunk);
            if (end >= content.length()) break;
            start = Math.max(end - OVERLAP, start + 1);
        }
        return values;
    }

    private List<Document> vectorDocuments(KnowledgeDocument entity, List<String> chunks) {
        List<Document> values = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("docId", entity.getId().toString());
            metadata.put("docTitle", entity.getTitle());
            metadata.put("chunkIndex", index);
            metadata.put("source", entity.getSource());
            enrichMetadata(metadata, entity);
            values.add(new Document("knowledge-" + entity.getId() + '-' + index, chunks.get(index), metadata));
        }
        return values;
    }

    private KnowledgeDocumentVO view(KnowledgeDocument entity) {
        KnowledgeDocumentVO value = new KnowledgeDocumentVO();
        value.setId(entity.getId()); value.setTitle(entity.getTitle()); value.setSource(entity.getSource());
        value.setSourceRef(entity.getSourceRef()); value.setChunkCount(entity.getChunkCount());
        value.setFileSize(entity.getFileSize()); value.setPeriod("all");
        value.setVectorStatus(entity.getVectorStatus()); value.setPipelineError(entity.getPipelineError());
        if (entity.getCreateTime() != null) value.setCreateTime(entity.getCreateTime()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return value;
    }

    private void validate(String source, String sourceRef, String content) {
        if (!SOURCES.contains(source)) throw new IllegalArgumentException("不支持的项目知识来源");
        if (sourceRef == null || sourceRef.isBlank()) throw new IllegalArgumentException("项目来源标识不能为空");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("项目知识内容不能为空");
    }

    private String error(RuntimeException error) {
        String value = error.getMessage();
        if (value == null || value.isBlank()) value = error.getClass().getSimpleName();
        return value.length() > 900 ? value.substring(0, 900) : value;
    }
}
