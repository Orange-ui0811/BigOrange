package com.orangejd.server.controller.app;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.dto.OrderCreateDTO;
import com.orangejd.pojo.dto.DeliverySubmitDTO;
import com.orangejd.pojo.vo.DeliveryAttachmentVO;
import com.orangejd.pojo.vo.OrderVO;
import com.orangejd.server.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/app/order")
@lombok.RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

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
    public Result<?> deliver(@PathVariable Long id, @RequestBody(required = false) DeliverySubmitDTO body) {
        orderService.deliver(id, body == null ? new DeliverySubmitDTO() : body);
        return Result.success();
    }

    @PostMapping("/{id}/delivery/attachment")
    public Result<DeliveryAttachmentVO> uploadDeliveryAttachment(@PathVariable Long id,
                                                                  @RequestParam("file") MultipartFile file) {
        return Result.success(orderService.uploadDeliveryAttachment(id, file));
    }

    @DeleteMapping("/{id}/delivery/attachment")
    public Result<?> deletePendingDeliveryAttachment(@PathVariable Long id,
                                                       @RequestBody Map<String, String> body) {
        orderService.deletePendingDeliveryAttachment(id, body.get("objectKey"));
        return Result.success();
    }

    @GetMapping("/{orderId}/delivery/{deliveryId}/attachment-url")
    public Result<String> deliveryAttachmentUrl(@PathVariable Long orderId, @PathVariable Long deliveryId) {
        return Result.success(orderService.getDeliveryAttachmentUrl(orderId, deliveryId));
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

    @DeleteMapping("/batch")
    public Result<?> batchDelete(@RequestBody java.util.List<Long> ids) {
        for (Long id : ids) orderService.delete(id);
        return Result.success();
    }
}
