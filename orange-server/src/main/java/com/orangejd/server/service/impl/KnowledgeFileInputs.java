package com.orangejd.server.service.impl;

import com.orangejd.pojo.entity.KnowledgeDocument;
import com.orangejd.pojo.vo.KnowledgeDocumentVO;
import com.orangejd.server.mapper.KnowledgeDocumentMapper;
import com.orangejd.server.service.DocumentService;
import com.orangejd.server.service.ProjectIdentityResolver;
import com.orangejd.server.foundation.external.ExternalAiClient;
import com.orangejd.server.foundation.ingestion.IngestionRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class KnowledgeFileInputs {
    public static String readContent(MultipartFile file) {
        try {
            String content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.getBytes())).toString();
            if (content.indexOf('\0') >= 0) throw new IllegalArgumentException("文件包含不支持的二进制内容");
            return content;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("文件必须使用 UTF-8 编码");
        } catch (IOException e) {
            throw new RuntimeException("文件读取失败", e);
        }
    }

    public static String sanitizeTitle(String title) {
        String cleaned = title.replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.isEmpty()) return "未命名.md";
        return cleaned.length() > 120 ? cleaned.substring(cleaned.length() - 120) : cleaned;
    }
}
