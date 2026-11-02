package com.orangejd.server.ragb.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.ragb.ingest.RagBChunker;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class RagBDocumentInputs {
    private final RagBProperties properties;
    private final ObjectMapper objectMapper;
    public RagBDocumentInputs(RagBProperties properties,ObjectMapper objectMapper) { this.properties=properties; this.objectMapper=objectMapper; }
    public String normalizeSource(String source) {
        return switch (source == null ? "" : source) {
            case "GITHUB", "BEGINNER_PROJECT", "CAREER_PROJECT", "JOB_POSTING", "MANUAL" -> source;
            default -> "MANUAL";
        };
    }

    public byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择文档");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".md") && !filename.endsWith(".txt")) {
            throw new IllegalArgumentException("B 版当前只接收 .md 和 .txt 文档");
        }
        if (file.getSize() > properties.getMaxFileBytes()) {
            throw new IllegalArgumentException("文档超过 B 版单文件上限");
        }
        try { return file.getBytes(); }
        catch (IOException ex) { throw new IllegalArgumentException("读取上传文档失败", ex); }
    }

    public String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("文档必须使用 UTF-8 编码", ex);
        }
    }

    public String sanitizeTitle(String value) {
        String title = value == null ? "未命名文档" : value.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (title.isBlank()) title = "未命名文档";
        return title.length() <= 255 ? title : title.substring(0, 255);
    }

    public String metadata(RagBDocument document, RagBChunker.ChunkDraft chunk,
                            Map<String, Object> documentMetadata) {
        try {
            Map<String, Object> values = new LinkedHashMap<>(documentMetadata);
            values.put("documentId", document.getId());
            values.put("chunkIndex", chunk.index());
            values.put("startOffset", chunk.startOffset());
            values.put("endOffset", chunk.endOffset());
            if (chunk.sectionPath() != null) values.put("sectionPath", chunk.sectionPath());
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("分块元数据序列化失败", ex);
        }
    }

    public String sha256(String content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 不可用", ex); }
    }

    public String abbreviate(String value) {
        if (value == null || value.isBlank()) return "未知清理错误";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
