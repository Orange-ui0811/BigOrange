package com.orangejd.server.controller.app;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.dto.WorkCreateDTO;
import com.orangejd.pojo.dto.WorkEditDTO;
import com.orangejd.pojo.dto.WorkQueryDTO;
import com.orangejd.pojo.vo.WorkDetailVO;
import com.orangejd.pojo.vo.WorkVO;
import com.orangejd.server.service.WorkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/app/work")
@lombok.RequiredArgsConstructor
public class AppWorkController {

    private final WorkService workService;

    @PostMapping
    public Result<WorkDetailVO> create(@RequestBody WorkCreateDTO dto) {
        return Result.success(workService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<WorkDetailVO> edit(@PathVariable Long id, @RequestBody WorkEditDTO dto) {
        return Result.success(workService.edit(id, dto));
    }

    @PostMapping("/{id}/submit")
    public Result<?> submitForReview(@PathVariable Long id) {
        workService.submitForReview(id);
        return Result.success();
    }

    @PostMapping("/{id}/withdraw")
    public Result<?> withdraw(@PathVariable Long id) {
        workService.withdraw(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        workService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/visibility")
    public Result<?> toggleVisibility(@PathVariable Long id) {
        workService.toggleVisibility(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<WorkDetailVO> detail(@PathVariable Long id) {
        return Result.success(workService.getDetail(id));
    }

    @GetMapping("/my")
    public Result<PageResult<WorkVO>> myWorks(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return Result.success(workService.listMyWorks(page, size));
    }

    @GetMapping("/public")
    public Result<PageResult<WorkVO>> publicList(WorkQueryDTO query) {
        return Result.success(workService.listPublic(query));
    }
}
