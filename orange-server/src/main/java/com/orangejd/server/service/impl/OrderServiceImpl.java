package com.orangejd.server.service.impl;

import com.orangejd.common.constant.MessageConstant;
import lombok.extern.slf4j.Slf4j;
import com.orangejd.common.constant.OrderStatus;
import com.orangejd.common.constant.PayStatus;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.result.PageResult;
import com.orangejd.common.util.PageUtil;
import com.orangejd.pojo.dto.OrderCreateDTO;
import com.orangejd.pojo.entity.*;
import com.orangejd.pojo.vo.OrderVO;
import com.orangejd.server.mapper.*;
import com.orangejd.server.service.OrderService;
import com.orangejd.server.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired private OrderMapper orderMapper;
    @Autowired private PaymentStageMapper paymentStageMapper;
    @Autowired private DeliveryMapper deliveryMapper;
    @Autowired private WorkMapper workMapper;
    @Autowired private QuoteMapper quoteMapper;
    @Autowired private ConversationMapper conversationMapper;
    @Autowired private NotificationService notificationService;

    @Override
    @Transactional
    public OrderVO create(OrderCreateDTO dto) {
        Long currentId = BaseContext.getCurrentId();
        if (dto.getTotalAmount() == null || dto.getTotalAmount().compareTo(java.math.BigDecimal.ZERO) <= 0)
            throw new BaseException("订单金额必须大于0");

        Order order = new Order();
        order.setSourceType(dto.getSourceType());
        order.setSourceId(dto.getSourceId());
        order.setBuyerId(currentId);
        order.setTotalAmount(dto.getTotalAmount());
        order.setOrderStatus(OrderStatus.CREATED);

        if ("QUOTE".equals(dto.getSourceType())) {
            Quote q = quoteMapper.selectById(dto.getSourceId());
            if (q == null) throw new BaseException("报价单不存在");
            if (!currentId.equals(q.getSenderId())) throw new BaseException("只有报价发起方可以从报价创建订单");
            if (!com.orangejd.common.constant.QuoteStatus.ACCEPTED.equals(q.getStatus()))
                throw new BaseException("报价单未被接受，无法创建订单");
            order.setSellerId(q.getReceiverId()); // 报价接收方是卖家
            Conversation conv = conversationMapper.selectById(q.getConversationId());
            if (conv != null) { order.setWorkId(conv.getWorkId()); }
        } else if ("WORK".equals(dto.getSourceType())) {
            Work w = workMapper.selectById(dto.getSourceId());
            if (w == null) throw new BaseException("作品不存在");
            order.setSellerId(w.getOwnerId());
            order.setWorkId(w.getId());
        } else {
            throw new BaseException("订单来源类型无效");
        }

        if (currentId.equals(order.getSellerId())) throw new BaseException("不能向自己下单");

        order.setDeposit(dto.getDeposit());
        order.setFinalAmount(dto.getFinalAmount());
        order.setOrderStatus(OrderStatus.WAITING_DEPOSIT);
        orderMapper.insert(order);

        // 创建定金支付阶段
        if (dto.getDeposit() != null && dto.getDeposit().compareTo(java.math.BigDecimal.ZERO) > 0) {
            PaymentStage ps = new PaymentStage();
            ps.setOrderId(order.getId()); ps.setStageType("DEPOSIT"); ps.setAmount(dto.getDeposit());
            paymentStageMapper.insert(ps);
        }
        // 创建尾款支付阶段
        if (dto.getFinalAmount() != null && dto.getFinalAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
            PaymentStage ps2 = new PaymentStage();
            ps2.setOrderId(order.getId()); ps2.setStageType("FINAL"); ps2.setAmount(dto.getFinalAmount());
            paymentStageMapper.insert(ps2);
        }

        notificationService.send(order.getSellerId(), "ORDER", "你有一个新订单", order.getId());
        notificationService.send(order.getBuyerId(), "ORDER", "订单已创建，请支付定金", order.getId());
        return getDetail(order.getId());
    }

    @Override
    public OrderVO getDetail(Long id) {
        OrderVO vo = orderMapper.selectVOById(id);
        if (vo == null) throw new BaseException("订单不存在");
        Long currentId = BaseContext.getCurrentId();
        if (!currentId.equals(vo.getBuyerId()) && !currentId.equals(vo.getSellerId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        vo.setPaymentStages(paymentStageMapper.selectByOrderId(id).stream().map(p -> {
            OrderVO.PaymentStageVO pvo = new OrderVO.PaymentStageVO();
            pvo.setId(p.getId()); pvo.setStageType(p.getStageType()); pvo.setAmount(p.getAmount());
            pvo.setPayStatus(p.getPayStatus()); pvo.setPayTime(p.getPayTime()); return pvo;
        }).collect(Collectors.toList()));
        vo.setDeliveries(deliveryMapper.selectByOrderId(id).stream().map(d -> {
            OrderVO.DeliveryVO dvo = new OrderVO.DeliveryVO();
            dvo.setId(d.getId()); dvo.setDelivererId(d.getDelivererId());
            dvo.setContent(d.getContent()); dvo.setAttachmentUrl(d.getAttachmentUrl());
            dvo.setCreateTime(d.getCreateTime()); return dvo;
        }).collect(Collectors.toList()));
        return vo;
    }

    @Override @Transactional
    public void payDeposit(Long id) {
        Order o = getAndCheckBuyer(id);
        if (!OrderStatus.WAITING_DEPOSIT.equals(o.getOrderStatus()))
            throw new BaseException("订单状态不允许支付定金");
        orderMapper.updateStatus(id, OrderStatus.DEPOSIT_PAID);
        log.info("支付定金 orderId={} buyerId={}", id, o.getBuyerId());
        updatePaymentStage(id, "DEPOSIT");
        notificationService.send(o.getSellerId(), "ORDER", "买家已支付定金", id);
        notificationService.send(o.getBuyerId(), "ORDER", "定金支付成功，等待卖家交付", id);
    }

    @Override @Transactional
    public void deliver(Long id, String content, String attachmentUrl) {
        Order o = getAndCheckSeller(id);
        if (!OrderStatus.DEPOSIT_PAID.equals(o.getOrderStatus()))
            throw new BaseException("定金支付后才能交付");
        orderMapper.updateStatus(id, OrderStatus.WAITING_FINAL_PAYMENT);
        Delivery d = new Delivery();
        d.setOrderId(id); d.setDelivererId(BaseContext.getCurrentId());
        d.setContent(content); d.setAttachmentUrl(attachmentUrl);
        deliveryMapper.insert(d);
        notificationService.send(o.getBuyerId(), "ORDER", "卖家已提交交付，请确认并支付尾款", id);
        notificationService.send(o.getSellerId(), "ORDER", "交付已提交，等待买家确认并支付尾款", id);
    }

    @Override @Transactional
    public void confirmDelivery(Long id) {
        Order o = getAndCheckBuyer(id);
        if (!OrderStatus.WAITING_FINAL_PAYMENT.equals(o.getOrderStatus()))
            throw new BaseException("当前状态不允许确认交付");
        orderMapper.updateStatus(id, OrderStatus.DELIVERED);
    }

    @Override @Transactional
    public void payFinal(Long id) {
        Order o = getAndCheckBuyer(id);
        if (!OrderStatus.WAITING_FINAL_PAYMENT.equals(o.getOrderStatus())
                && !OrderStatus.DELIVERED.equals(o.getOrderStatus()))
            throw new BaseException("当前状态不允许支付尾款");
        orderMapper.updateStatus(id, OrderStatus.FINAL_PAID);
        updatePaymentStage(id, "FINAL");
        orderMapper.updateStatus(id, OrderStatus.COMPLETED);
        log.info("支付尾款 orderId={} buyerId={}", id, o.getBuyerId());
        notificationService.send(o.getSellerId(), "ORDER", "买家已支付尾款，订单完成", id);
        notificationService.send(o.getBuyerId(), "ORDER", "尾款支付成功，订单已完成", id);
    }

    @Override @Transactional
    public void cancel(Long id) {
        Order o = orderMapper.selectById(id);
        if (o == null) throw new BaseException("订单不存在");
        Long currentId = BaseContext.getCurrentId();
        if (!currentId.equals(o.getBuyerId()) && !currentId.equals(o.getSellerId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        if (OrderStatus.COMPLETED.equals(o.getOrderStatus()) || OrderStatus.CANCELLED.equals(o.getOrderStatus()))
            throw new BaseException("已完成或已取消的订单不可操作");
        orderMapper.updateStatus(id, OrderStatus.CANCELLED);
        Long otherId = currentId.equals(o.getBuyerId()) ? o.getSellerId() : o.getBuyerId();
        notificationService.send(otherId, "ORDER", "订单已被取消", id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Order o = orderMapper.selectById(id);
        if (o == null) throw new BaseException("订单不存在");
        Long currentId = BaseContext.getCurrentId();
        if (!currentId.equals(o.getBuyerId()) && !currentId.equals(o.getSellerId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        if (!OrderStatus.COMPLETED.equals(o.getOrderStatus()) && !OrderStatus.CANCELLED.equals(o.getOrderStatus()))
            throw new BaseException("只能删除已完成或已取消的订单");
        orderMapper.deleteById(id);
    }

    @Override
    public PageResult<OrderVO> listBuy(int page, int size) {
        size = PageUtil.capSize(size);
        Long currentId = BaseContext.getCurrentId();
        int offset = (page - 1) * size;
        int total = orderMapper.countByBuyerId(currentId);
        return new PageResult<>(total, orderMapper.selectByBuyerId(currentId, offset, size));
    }

    @Override
    public PageResult<OrderVO> listSell(int page, int size) {
        size = PageUtil.capSize(size);
        Long currentId = BaseContext.getCurrentId();
        int offset = (page - 1) * size;
        int total = orderMapper.countBySellerId(currentId);
        return new PageResult<>(total, orderMapper.selectBySellerId(currentId, offset, size));
    }

    private Order getAndCheckBuyer(Long id) {
        Order o = orderMapper.selectById(id); if (o == null) throw new BaseException("订单不存在");
        if (!BaseContext.getCurrentId().equals(o.getBuyerId())) throw new BaseException("只有买家可以此操作");
        return o;
    }
    private Order getAndCheckSeller(Long id) {
        Order o = orderMapper.selectById(id); if (o == null) throw new BaseException("订单不存在");
        if (!BaseContext.getCurrentId().equals(o.getSellerId())) throw new BaseException("只有卖家可以此操作");
        return o;
    }
    private void updatePaymentStage(Long orderId, String stageType) {
        List<PaymentStage> stages = paymentStageMapper.selectByOrderId(orderId);
        for (PaymentStage ps : stages) {
            if (stageType.equals(ps.getStageType()) && PayStatus.UNPAID.equals(ps.getPayStatus())) {
                paymentStageMapper.updatePayStatus(ps.getId(), PayStatus.PAID);
            }
        }
    }
}
