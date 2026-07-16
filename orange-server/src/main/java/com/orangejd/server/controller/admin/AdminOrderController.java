package com.orangejd.server.controller.admin;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.vo.OrderVO;
import com.orangejd.server.mapper.DeliveryMapper;
import com.orangejd.server.mapper.OrderMapper;
import com.orangejd.server.mapper.PaymentStageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/orders")
public class AdminOrderController {

    @Autowired private OrderMapper orderMapper;
    @Autowired private PaymentStageMapper paymentStageMapper;
    @Autowired private DeliveryMapper deliveryMapper;

    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable Long id) {
        OrderVO vo = orderMapper.selectVOById(id);
        if (vo != null) {
            vo.setPaymentStages(paymentStageMapper.selectByOrderId(id).stream().map(p -> {
                OrderVO.PaymentStageVO pv = new OrderVO.PaymentStageVO();
                pv.setId(p.getId()); pv.setStageType(p.getStageType()); pv.setAmount(p.getAmount());
                pv.setPayStatus(p.getPayStatus()); pv.setPayTime(p.getPayTime()); return pv;
            }).collect(Collectors.toList()));
            vo.setDeliveries(deliveryMapper.selectByOrderId(id).stream().map(d -> {
                OrderVO.DeliveryVO dv = new OrderVO.DeliveryVO();
                dv.setId(d.getId()); dv.setDelivererId(d.getDelivererId());
                dv.setContent(d.getContent()); dv.setAttachmentUrl(d.getAttachmentUrl());
                dv.setCreateTime(d.getCreateTime()); return dv;
            }).collect(Collectors.toList()));
        }
        return Result.success(vo);
    }

    @GetMapping
    public Result<PageResult<OrderVO>> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        int s = com.orangejd.common.util.PageUtil.capSize(size);
        int offset = com.orangejd.common.util.PageUtil.offset(page, s);
        return Result.success(new PageResult<>(orderMapper.countAllOrders(),
                orderMapper.selectAll(offset, s)));
    }

    @DeleteMapping("/batch")
    public Result<?> batchDelete(@RequestBody java.util.List<Long> ids) {
        return Result.error("订单属于财务审计记录，禁止物理删除");
    }
}
