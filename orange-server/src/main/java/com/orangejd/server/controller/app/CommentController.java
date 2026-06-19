package com.orangejd.server.controller.app;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.dto.CommentCreateDTO;
import com.orangejd.pojo.vo.CommentVO;
import com.orangejd.server.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/app/comment")
public class CommentController {

    @Autowired private CommentService commentService;

    @PostMapping
    public Result<?> create(@RequestBody CommentCreateDTO dto) {
        commentService.create(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        commentService.delete(id);
        return Result.success();
    }

    @GetMapping("/{targetType}/{targetId}")
    public Result<PageResult<CommentVO>> list(@PathVariable String targetType, @PathVariable Long targetId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        return Result.success(commentService.listByTarget(targetType, targetId, page, size));
    }

    @GetMapping("/my")
    public Result<PageResult<CommentVO>> myComments(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "50") int size) {
        return Result.success(commentService.listMyComments(page, size));
    }

    @GetMapping("/on-my-content")
    public Result<PageResult<CommentVO>> onMyContent(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "50") int size) {
        return Result.success(commentService.listCommentsOnMyContent(page, size));
    }
}
