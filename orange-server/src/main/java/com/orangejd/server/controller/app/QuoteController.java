package com.orangejd.server.controller.app;

import com.orangejd.common.result.Result;
import com.orangejd.pojo.dto.QuoteCreateDTO;
import com.orangejd.pojo.entity.Quote;
import com.orangejd.server.service.QuoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/app/quote")
@lombok.RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;

    @PostMapping
    public Result<Quote> create(@RequestBody QuoteCreateDTO dto) {
        return Result.success(quoteService.create(dto));
    }

    @PutMapping("/{id}/accept")
    public Result<?> accept(@PathVariable Long id) {
        quoteService.accept(id);
        return Result.success();
    }

    @PutMapping("/{id}/reject")
    public Result<?> reject(@PathVariable Long id) {
        quoteService.reject(id);
        return Result.success();
    }

    @PutMapping("/{id}/cancel")
    public Result<?> cancel(@PathVariable Long id) {
        quoteService.cancel(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<Quote> detail(@PathVariable Long id) {
        return Result.success(quoteService.getDetail(id));
    }

    @GetMapping("/by-conversation/{conversationId}")
    public Result<List<Quote>> byConversation(@PathVariable Long conversationId) {
        return Result.success(quoteService.getByConversation(conversationId));
    }
}
