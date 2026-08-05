package com.orangejd.server.controller.admin;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.vo.OrderVO;
import com.orangejd.server.service.AdminOrderReadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/orders")
@lombok.RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderReadService orderService;

    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable Long id) {
        return Result.success(orderService.detail(id));
    }

    @GetMapping
    public Result<PageResult<OrderVO>> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        int s = com.orangejd.common.util.PageUtil.capSize(size);
        int offset = com.orangejd.common.util.PageUtil.offset(page, s);
        return Result.success(orderService.list(offset, s));
    }

    @DeleteMapping("/batch")
    public Result<?> batchDelete(@RequestBody java.util.List<Long> ids) {
        return Result.error("订单属于财务审计记录，禁止物理删除");
    }
}
