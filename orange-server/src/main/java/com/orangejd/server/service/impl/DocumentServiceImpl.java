package com.orangejd.server.service.impl;

import com.orangejd.pojo.entity.KnowledgeDocument;
import com.orangejd.pojo.vo.KnowledgeDocumentVO;
import com.orangejd.server.mapper.KnowledgeDocumentMapper;
import com.orangejd.server.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);

    private static final int MAX_FILE_SIZE = 1_048_576;
    private static final int MAX_CHUNKS = 100;
    private static final int CHUNK_SIZE = 500;
    private static final int OVERLAP = 50;
    private static final int MAX_CHUNK_CHARS = 400;
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String SOURCE_GITHUB = "GITHUB";

    @Autowired private KnowledgeDocumentMapper mapper;
    @Autowired(required = false) private VectorStore vectorStore;

    @Value("${orange.ai-trend.endpoint:https://ai.gitee.com/v1}")
    private String llmEndpoint;
    @Value("${orange.ai-trend.api-key:}")
    private String llmApiKey;
    @Value("${orange.ai-trend.model:DeepSeek-V4-Pro}")
    private String llmModel;

    private final RestTemplate llmRestTemplate = new RestTemplate();

    // ---- upload ----

    @Override
    public KnowledgeDocumentVO upload(MultipartFile file) {
        String title = file.getOriginalFilename();
        if (title == null || title.isEmpty()) title = "未命名.md";
        validateFile(file, title);
        String content = readContent(file);
        return storeDocument(title, SOURCE_MANUAL, null, content, file.getSize());
    }

    // ---- importFromGithub ----

    @Override
    public KnowledgeDocumentVO importFromGithub(String fullName, String content, String period) {
        String sourceRef = "week".equals(period) ? fullName + "|week" : fullName;
        // 翻译为中文
        String chineseContent = translateReadme(fullName, content);
        // 提取翻译后第一行 Markdown 标题作为显示名
        String title = extractTitle(chineseContent, fullName);
        return storeDocument(title, SOURCE_GITHUB, sourceRef, chineseContent, (long) content.length());
    }

    /** 提取 Markdown 第一个 # 标题，无标题则用 repo 名 */
    private String extractTitle(String content, String fallback) {
        if (content == null || content.isEmpty()) return fallback;
        for (String line : content.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ") && !trimmed.startsWith("# [")) {
                String title = trimmed.replaceAll("^#+\\s*", "").trim();
                if (title.length() > 3 && title.length() < 120) return title;
            }
        }
        return fallback;
    }

    @Override
    public void importJobPosting(Long jobId, String position, String direction, String displayContent) {
        String sourceRef = "job_" + jobId;
        // 删旧记录（幂等同步）
        KnowledgeDocument existing = mapper.selectBySourceRef(sourceRef);
        if (existing != null) {
            delete(existing.getId());
        }
        storeDocument(position, "JOB_POSTING", sourceRef, displayContent, (long) displayContent.length());
    }

    @Override
    public boolean existsBySourceRef(String sourceRef) {
        if (sourceRef == null) return false;
        return mapper.selectBySourceRef(sourceRef) != null;
    }

    // ---- listAll ----

    @Override
    public List<KnowledgeDocumentVO> listAll() {
        List<KnowledgeDocument> entities = mapper.selectAll();
        List<KnowledgeDocumentVO> result = new ArrayList<>(entities.size());
        for (KnowledgeDocument e : entities) result.add(toVO(e));
        return result;
    }

    // ---- delete ----

    @Override
    public void delete(Long id) {
        KnowledgeDocument doc = mapper.selectById(id);
        if (doc == null) return;
        if (vectorStore != null) {
            try { vectorStore.delete("docId == '" + id + "'"); }
            catch (Exception e) { log.debug("[RAG] 向量删除跳过 (已知局限): docId={}", id); }
        }
        mapper.deleteById(id);
        log.info("[RAG] 文档已删除: id={}, title={}", id, doc.getTitle());
    }

    // ---- 清理过期 weekly 文档 ----

    public int cleanExpiredWeekly(String beforeDate) {
        List<KnowledgeDocument> expired = mapper.selectOldGithubWeekly(beforeDate);
        int count = 0;
        for (KnowledgeDocument doc : expired) {
            delete(doc.getId());
            count++;
        }
        if (count > 0) log.info("[RAG] 清理过期 weekly 文档 {} 篇", count);
        return count;
    }

    // ==================== 内部方法 ====================

    private KnowledgeDocumentVO storeDocument(String title, String source,
                                              String sourceRef, String content, long fileSize) {
        List<String> chunks = splitChunks(content);
        chunks = sanitizeChunks(chunks, title);

        KnowledgeDocument entity = new KnowledgeDocument();
        entity.setTitle(title);
        entity.setSource(source);
        entity.setSourceRef(sourceRef);
        entity.setChunkCount(chunks.size());
        entity.setFileSize(fileSize);
        entity.setContent(content);
        mapper.insert(entity);

        if (vectorStore != null && !chunks.isEmpty()) {
            List<Document> docs = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("docId", entity.getId().toString());
                meta.put("docTitle", title);
                meta.put("chunkIndex", i);
                meta.put("source", source);
                docs.add(new Document(chunks.get(i), meta));
            }
            vectorStore.add(docs);
            log.info("[RAG] 向量存储完成: title={}, chunks={}", title, chunks.size());
        } else if (vectorStore == null) {
            log.warn("[RAG] VectorStore 不可用——文档未向量化: title={}", title);
        }

        log.info("[RAG] 文档入库: id={}, title={}, source={}, chunks={}", entity.getId(), title, source, chunks.size());
        return toVO(entity);
    }

    /**
     * 翻译 README 为中文（保留 Markdown 结构和代码块）
     */
    private String translateReadme(String fullName, String content) {
        if (content == null || content.isEmpty()) return content;
        if (llmApiKey == null || llmApiKey.isEmpty()) {
            log.debug("[RAG] LLM API key 未配置，跳过翻译");
            return content;
        }
        try {
            // 只翻译前 4000 字（避免 token 爆炸）
            String toTranslate = content.length() > 4000 ? content.substring(0, 4000) : content;

            String prompt = "请将以下 GitHub README 内容翻译为中文。" +
                "保留 Markdown 格式（标题、列表、代码块）。代码块内容不翻译。只输出翻译结果，不要额外说明。";

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", llmModel);
            List<Map<String, String>> msgs = new ArrayList<>();
            msgs.add(Map.of("role", "system", "content", prompt));
            msgs.add(Map.of("role", "user", "content", toTranslate));
            body.put("messages", msgs);
            body.put("temperature", 0.1);

            String url = llmEndpoint.replaceAll("/$", "") + "/chat/completions";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + llmApiKey);
            ResponseEntity<Map> resp = llmRestTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.getBody().get("choices");
            String translated = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
            log.info("[RAG] 翻译完成: {} ({} → {} chars)", fullName, content.length(), translated.length());
            return translated;
        } catch (Exception e) {
            log.warn("[RAG] 翻译失败 {}: {}", fullName, e.getMessage());
            return content; // 降级：用原文
        }
    }

    // ---- 切片 ----

    List<String> splitChunks(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length() && chunks.size() < MAX_CHUNKS) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            if (end < text.length()) {
                int nl = text.lastIndexOf('\n', end);
                if (nl > start + CHUNK_SIZE / 2) end = nl;
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) chunks.add(chunk);
            if (end >= text.length()) break;
            start = Math.max(end - OVERLAP, start + 1);
        }
        return chunks;
    }

    private List<String> sanitizeChunks(List<String> chunks, String title) {
        List<String> cleaned = new ArrayList<>();
        for (String chunk : chunks) {
            if (chunk.length() > MAX_CHUNK_CHARS) chunk = chunk.substring(0, MAX_CHUNK_CHARS);
            String stripped = chunk.replaceAll("\\s+", "").replaceAll("[#*\\-`>|]+", "");
            if (stripped.length() >= 10) cleaned.add(chunk);
        }
        if (cleaned.size() != chunks.size())
            log.info("[RAG] 切片校验: {} → {} (过滤 {})", chunks.size(), cleaned.size(), chunks.size() - cleaned.size());
        return cleaned;
    }

    private void validateFile(MultipartFile file, String title) {
        if (file.isEmpty()) throw new IllegalArgumentException("文件为空");
        if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("文件过大");
        String lower = title.toLowerCase();
        if (!lower.endsWith(".md") && !lower.endsWith(".txt"))
            throw new IllegalArgumentException("仅支持 .md 和 .txt");
    }

    private String readContent(MultipartFile file) {
        try { return new String(file.getBytes(), "UTF-8"); }
        catch (IOException e) { throw new RuntimeException("文件读取失败", e); }
    }

    private String resolvePeriod(KnowledgeDocument e) {
        if (!SOURCE_GITHUB.equals(e.getSource())) return "all";
        String ref = e.getSourceRef();
        return (ref != null && ref.endsWith("|week")) ? "week" : "all";
    }

    private KnowledgeDocumentVO toVO(KnowledgeDocument e) {
        KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
        vo.setId(e.getId()); vo.setTitle(e.getTitle());
        vo.setSource(e.getSource()); vo.setSourceRef(e.getSourceRef());
        vo.setChunkCount(e.getChunkCount() != null ? e.getChunkCount() : 0);
        vo.setFileSize(e.getFileSize());
        vo.setPeriod(resolvePeriod(e));
        if (e.getCreateTime() != null)
            vo.setCreateTime(e.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return vo;
    }
}
