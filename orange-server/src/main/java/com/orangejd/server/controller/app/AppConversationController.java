package com.orangejd.server.controller.app;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.dto.ConversationCreateDTO;
import com.orangejd.pojo.vo.ConversationVO;
import com.orangejd.server.service.IConversationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/app/conversation")
public class AppConversationController {

    @Autowired private IConversationService conversationService;

    @PostMapping
    public Result<ConversationVO> create(@RequestBody ConversationCreateDTO dto) {
        return Result.success(conversationService.create(dto));
    }

    @GetMapping("/list")
    public Result<PageResult<ConversationVO>> list(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return Result.success(conversationService.listMyConversations(page, size));
    }

    @GetMapping("/{id}")
    public Result<ConversationVO> detail(@PathVariable Long id) {
        return Result.success(conversationService.getDetail(id));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        conversationService.deleteConversation(id);
        return Result.success();
    }
}
