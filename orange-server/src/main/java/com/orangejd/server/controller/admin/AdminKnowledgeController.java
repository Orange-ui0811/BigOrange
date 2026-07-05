package com.orangejd.server.controller.admin;

import com.orangejd.common.result.Result;
import com.orangejd.pojo.entity.KnowledgeDocument;
import com.orangejd.pojo.vo.KnowledgeDocumentVO;
import com.orangejd.server.mapper.KnowledgeDocumentMapper;
import com.orangejd.server.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/knowledge/document")
public class AdminKnowledgeController {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        List<KnowledgeDocument> all = knowledgeDocumentMapper.selectAll();
        long manual = 0, github = 0, totalChunks = 0;
        for (KnowledgeDocument d : all) {
            if ("GITHUB".equals(d.getSource())) github++; else manual++;
            totalChunks += d.getChunkCount() != null ? d.getChunkCount() : 0;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalDocs", all.size());
        map.put("manualCount", manual);
        map.put("githubCount", github);
        map.put("totalChunks", totalChunks);
        return Result.success(map);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        KnowledgeDocument doc = knowledgeDocumentMapper.selectById(id);
        if (doc == null) return Result.error("文档不存在");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", doc.getId());
        map.put("title", doc.getTitle());
        map.put("source", doc.getSource());
        map.put("chunkCount", doc.getChunkCount());
        map.put("content", doc.getContent());
        return Result.success(map);
    }

    @PostMapping("/upload")
    public Result<KnowledgeDocumentVO> upload(@RequestParam("file") MultipartFile file) {
        try {
            return Result.success(documentService.upload(file));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<List<KnowledgeDocumentVO>> list() {
        return Result.success(documentService.listAll());
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.success();
    }
}
