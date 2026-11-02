package com.orangejd.server.controller.admin;

import com.orangejd.common.result.Result;
import com.orangejd.server.ragb.service.RagBDocumentService;
import org.springframework.web.bind.annotation.*;

/** Uses the existing ADMIN authentication interceptor; FAQ is a reviewed platform source. */
@RestController
@RequestMapping("/admin/customer/knowledge")
public class AdminCustomerKnowledgeController {
    private final RagBDocumentService documents;
    public AdminCustomerKnowledgeController(RagBDocumentService documents) { this.documents=documents; }
    @PostMapping("/faq")
    public Result<?> faq(@RequestBody FaqInput input) {
        if(input.key()==null || !input.key().matches("[a-zA-Z0-9_-]{3,80}") || input.content()==null
            || input.content().isBlank() || input.content().length()>200000) throw new IllegalArgumentException("FAQ标识或内容无效");
        String title=input.title()==null || input.title().isBlank()?"平台客服FAQ":input.title();
        return Result.success(documents.importSource("MANUAL","platform-faq:"+input.key(),title,input.content()));
    }
    public record FaqInput(String key,String title,String content) {}
}
