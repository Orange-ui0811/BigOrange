package com.orangejd.server.controller.admin;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.vo.CommentVO;
import com.orangejd.server.service.ICommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/comment")
public class AdminCommentController {

    @Autowired private ICommentService commentService;

    @GetMapping("/{targetType}/{targetId}")
    public Result<PageResult<CommentVO>> list(@PathVariable String targetType, @PathVariable Long targetId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        return Result.success(commentService.listByTarget(targetType, targetId, page, size));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        commentService.delete(id);
        return Result.success();
    }

    @GetMapping("/all")
    public Result<PageResult<CommentVO>> all(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "50") int size) {
        return Result.success(commentService.listAll(page, size));
    }
}
