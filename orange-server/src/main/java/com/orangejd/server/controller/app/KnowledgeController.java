package com.orangejd.server.controller.app;

import com.orangejd.common.result.Result;
import com.orangejd.pojo.vo.KnowledgeDocumentVO;
import com.orangejd.server.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/app/knowledge/document")
public class KnowledgeController {

    @Autowired
    private DocumentService documentService;

    @PostMapping("/upload")
    public Result<KnowledgeDocumentVO> upload(@RequestParam("file") MultipartFile file) {
        try {
            KnowledgeDocumentVO vo = documentService.upload(file);
            return Result.success(vo);
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
