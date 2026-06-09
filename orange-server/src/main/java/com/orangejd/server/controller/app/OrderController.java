package com.orangejd.server.controller.app;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.dto.OrderCreateDTO;
import com.orangejd.pojo.vo.OrderVO;
import com.orangejd.server.service.IOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/app/order")
public class OrderController {

    @Autowired private IOrderService orderService;

    @PostMapping
    public Result<OrderVO> create(@RequestBody OrderCreateDTO dto) {
        return Result.success(orderService.create(dto));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable Long id) {
        return Result.success(orderService.getDetail(id));
    }

    @GetMapping("/buy")
    public Result<PageResult<OrderVO>> buyList(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return Result.success(orderService.listBuy(page, size));
    }

    @GetMapping("/sell")
    public Result<PageResult<OrderVO>> sellList(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return Result.success(orderService.listSell(page, size));
    }

    @PutMapping("/{id}/deposit")
    public Result<?> payDeposit(@PathVariable Long id) {
        orderService.payDeposit(id);
        return Result.success();
    }

    @PutMapping("/{id}/deliver")
    public Result<?> deliver(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String content = body != null ? body.getOrDefault("content", "") : "";
        String url = body != null ? body.getOrDefault("attachmentUrl", null) : null;
        orderService.deliver(id, content, url);
        return Result.success();
    }

    @PutMapping("/{id}/confirm")
    public Result<?> confirm(@PathVariable Long id) {
        orderService.confirmDelivery(id);
        return Result.success();
    }

    @PutMapping("/{id}/final-pay")
    public Result<?> payFinal(@PathVariable Long id) {
        orderService.payFinal(id);
        return Result.success();
    }

    @PutMapping("/{id}/cancel")
    public Result<?> cancel(@PathVariable Long id) {
        orderService.cancel(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        orderService.delete(id);
        return Result.success();
    }
}
