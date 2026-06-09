package com.orangejd.server.controller.admin;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.dto.WorkQueryDTO;
import com.orangejd.pojo.vo.WorkDetailVO;
import com.orangejd.pojo.vo.WorkVO;
import com.orangejd.server.service.WorkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/work")
public class AdminWorkController {

    @Autowired private WorkService workService;

    @GetMapping("/{id}")
    public Result<WorkDetailVO> detail(@PathVariable Long id) {
        return Result.success(workService.getDetail(id));
    }

    @GetMapping
    public Result<PageResult<WorkVO>> listAll(WorkQueryDTO query) {
        return Result.success(workService.listAll(query));
    }

    @PutMapping("/{id}/offline")
    public Result<?> offline(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "管理员下架") : "管理员下架";
        workService.offline(id, reason);
        return Result.success();
    }
}
