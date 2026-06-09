package com.orangejd.server.controller.admin;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.vo.OrderVO;
import com.orangejd.server.mapper.OrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/orders")
public class AdminOrderController {

    @Autowired private OrderMapper orderMapper;

    @GetMapping
    public Result<PageResult<OrderVO>> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        int s = com.orangejd.common.util.PageUtil.capSize(size);
        int offset = (page - 1) * s;
        return Result.success(new PageResult<>(orderMapper.countAllOrders(),
                orderMapper.selectAll(offset, s)));
    }
}
