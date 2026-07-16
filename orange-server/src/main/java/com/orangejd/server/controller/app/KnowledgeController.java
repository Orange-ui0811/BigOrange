package com.orangejd.server.controller.app;

import com.orangejd.common.result.Result;
import com.orangejd.pojo.vo.KnowledgeDocumentVO;
import com.orangejd.server.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/app/knowledge/document")
public class KnowledgeController {

    @Autowired
    private DocumentService documentService;

    @GetMapping("/list")
    public Result<List<KnowledgeDocumentVO>> list() {
        return Result.success(documentService.listAll());
    }
}
