package com.orangejd.server.controller.app;

import com.orangejd.common.result.Result;
import com.orangejd.pojo.dto.MessageSendDTO;
import com.orangejd.pojo.entity.Message;
import com.orangejd.server.service.IMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/app/message")
public class AppMessageController {

    @Autowired private IMessageService messageService;

    @PostMapping
    public Result<Message> send(@RequestBody MessageSendDTO dto) {
        return Result.success(messageService.send(dto));
    }

    @GetMapping("/{conversationId}")
    public Result<List<Message>> list(@PathVariable Long conversationId,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        return Result.success(messageService.getMessages(conversationId, page, size));
    }

    @PutMapping("/{conversationId}/read")
    public Result<?> markRead(@PathVariable Long conversationId) {
        messageService.markAsRead(conversationId);
        return Result.success();
    }
}
