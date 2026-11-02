package com.orangejd.server.service;

import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.vo.OrderVO;
import com.orangejd.server.mapper.DeliveryMapper;
import com.orangejd.server.mapper.OrderMapper;
import com.orangejd.server.mapper.PaymentStageMapper;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

@Service
public class AdminOrderReadService {
    private final OrderMapper orders;
    private final PaymentStageMapper stages;
    private final DeliveryMapper deliveries;
    public AdminOrderReadService(OrderMapper orders, PaymentStageMapper stages, DeliveryMapper deliveries) {
        this.orders = orders; this.stages = stages; this.deliveries = deliveries;
    }
    public OrderVO detail(Long id) {
        OrderVO value = orders.selectVOById(id);
        if (value == null) return null;
        value.setPaymentStages(stages.selectByOrderId(id).stream().map(item -> {
            OrderVO.PaymentStageVO result = new OrderVO.PaymentStageVO();
            result.setId(item.getId()); result.setStageType(item.getStageType()); result.setAmount(item.getAmount());
            result.setPayStatus(item.getPayStatus()); result.setPayTime(item.getPayTime()); return result;
        }).collect(Collectors.toList()));
        value.setDeliveries(deliveries.selectByOrderId(id).stream().map(item -> {
            OrderVO.DeliveryVO result = new OrderVO.DeliveryVO();
            result.setId(item.getId()); result.setDelivererId(item.getDelivererId()); result.setContent(item.getContent());
            result.setAttachmentUrl(item.getAttachmentUrl()); result.setCreateTime(item.getCreateTime()); return result;
        }).collect(Collectors.toList()));
        return value;
    }
    public PageResult<OrderVO> list(int offset, int size) {
        return new PageResult<>(orders.countAllOrders(), orders.selectAll(offset, size));
    }
}
