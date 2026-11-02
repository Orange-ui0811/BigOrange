package com.orangejd.server.service;

import com.orangejd.common.constant.BrandConstant;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.constant.OrderStatus;
import com.orangejd.common.constant.PayStatus;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.dto.OrderPaymentCreateDTO;
import com.orangejd.pojo.entity.Order;
import com.orangejd.pojo.entity.OrderPayment;
import com.orangejd.pojo.entity.OrderPaymentCallbackEvent;
import com.orangejd.pojo.entity.PaymentStage;
import com.orangejd.server.config.EpayProperties;
import com.orangejd.server.mapper.OrderMapper;
import com.orangejd.server.mapper.OrderPaymentCallbackEventMapper;
import com.orangejd.server.mapper.OrderPaymentMapper;
import com.orangejd.server.mapper.PaymentStageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Owns creation and exactly-once application of real ePay order-stage payments. */
@Service
public class OrderPaymentApplicationService {
    private static final Logger log = LoggerFactory.getLogger(OrderPaymentApplicationService.class);
    private static final Set<String> STAGES = Set.of("DEPOSIT", "FINAL");
    private static final Set<String> METHODS = Set.of("wxpay", "alipay");

    private final EpayProperties epayProperties;
    private final EpayService epayService;
    private final OrderMapper orderMapper;
    private final PaymentStageMapper paymentStageMapper;
    private final OrderPaymentMapper orderPaymentMapper;
    private final OrderPaymentCallbackEventMapper callbackEventMapper;
    private final NotificationService notificationService;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public OrderPaymentApplicationService(EpayProperties epayProperties,
                                          EpayService epayService,
                                          OrderMapper orderMapper,
                                          PaymentStageMapper paymentStageMapper,
                                          OrderPaymentMapper orderPaymentMapper,
                                          OrderPaymentCallbackEventMapper callbackEventMapper,
                                          NotificationService notificationService,
                                          RateLimitService rateLimitService,
                                          ObjectMapper objectMapper) {
        this.epayProperties = epayProperties;
        this.epayService = epayService;
        this.orderMapper = orderMapper;
        this.paymentStageMapper = paymentStageMapper;
        this.orderPaymentMapper = orderPaymentMapper;
        this.callbackEventMapper = callbackEventMapper;
        this.notificationService = notificationService;
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, String> create(OrderPaymentCreateDTO dto) {
        if (!epayProperties.isConfigured() || !epayProperties.isOrderPaymentsEnabled()) {
            throw new BaseException("产品订单在线支付尚未开放");
        }
        if (dto == null || dto.getOrderId() == null) throw new BaseException("订单不能为空");
        String stageType = upper(dto.getStageType());
        String method = lower(dto.getMethod());
        if (!STAGES.contains(stageType)) throw new BaseException("支付阶段无效");
        if (!METHODS.contains(method)) throw new BaseException("支付方式无效");

        Long accountId = BaseContext.getCurrentId();
        if (!rateLimitService.allow("order-payment-create:account:" + accountId, 10, 300)) {
            throw new BaseException("创建支付过于频繁，请稍后重试");
        }

        Order order = orderMapper.selectByIdForUpdate(dto.getOrderId());
        if (order == null) throw new BaseException("订单不存在");
        if (!accountId.equals(order.getBuyerId())) throw new BaseException("只有买家可以付款");
        requirePayableOrderState(order, stageType);

        PaymentStage stage = paymentStageMapper.selectByOrderAndTypeForUpdate(order.getId(), stageType);
        if (stage == null || stage.getAmount() == null || stage.getAmount().signum() <= 0) {
            throw new BaseException("订单支付阶段不存在");
        }
        if (PayStatus.PAID.equals(stage.getPayStatus())) throw new BaseException("该阶段已经支付");

        OrderPayment payment = orderPaymentMapper.selectByStageForUpdate(stage.getId());
        if (payment == null) {
            payment = new OrderPayment();
            payment.setTradeNo("ORD_" + UUID.randomUUID().toString().replace("-", ""));
            payment.setOrderId(order.getId());
            payment.setPaymentStageId(stage.getId());
            payment.setPayerAccountId(accountId);
            payment.setStageType(stageType);
            payment.setAmount(stage.getAmount());
            payment.setPaymentMethod(method);
            payment.setPaymentProvider("epay");
            payment.setStatus("PENDING");
            orderPaymentMapper.insert(payment);
        } else {
            if ("SUCCESS".equals(payment.getStatus())) throw new BaseException("该阶段已经支付");
            if (!"PENDING".equals(payment.getStatus())) throw new BaseException("支付流水状态异常");
            if (!method.equals(payment.getPaymentMethod())) {
                throw new BaseException("已有未完成支付，请继续使用原支付方式");
            }
        }

        String subject = BrandConstant.PUBLIC_NAME + " 订单#" + order.getId()
                + ("DEPOSIT".equals(stageType) ? " 定金" : " 尾款");
        String payUrl = epayService.createPayment(
                payment.getTradeNo(), payment.getAmount(), payment.getPaymentMethod(), subject);

        Map<String, String> result = new LinkedHashMap<>();
        result.put("payUrl", payUrl);
        result.put("tradeNo", payment.getTradeNo());
        result.put("status", payment.getStatus());
        result.put("method", payment.getPaymentMethod());
        return result;
    }

    public Map<String, Object> status(String tradeNo) {
        if (tradeNo == null || tradeNo.isBlank() || tradeNo.length() > 64) {
            throw new BaseException("支付流水号无效");
        }
        OrderPayment payment = orderPaymentMapper.selectByTradeNo(tradeNo);
        if (payment == null || !BaseContext.getCurrentId().equals(payment.getPayerAccountId())) {
            throw new BaseException("支付流水不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tradeNo", payment.getTradeNo());
        result.put("orderId", payment.getOrderId());
        result.put("stageType", payment.getStageType());
        result.put("amount", payment.getAmount());
        result.put("method", payment.getPaymentMethod());
        result.put("status", payment.getStatus());
        result.put("completeTime", payment.getCompleteTime());
        return result;
    }

    @Transactional
    public void processEpayCallback(Map<String, String> params) {
        if (!epayService.verify(params)) throw new BaseException("支付回调验签失败");
        String tradeNo = required(params, "out_trade_no", 64);
        String providerTradeNo = required(params, "trade_no", 128);
        String tradeStatus = required(params, "trade_status", 32);
        String method = required(params, "type", 32);

        OrderPayment snapshot = orderPaymentMapper.selectByTradeNo(tradeNo);
        if (snapshot == null) throw new BaseException("订单支付流水不存在");

        Order order = orderMapper.selectByIdForUpdate(snapshot.getOrderId());
        if (order == null) throw new BaseException("订单不存在");
        PaymentStage stage = paymentStageMapper.selectByOrderAndTypeForUpdate(
                snapshot.getOrderId(), snapshot.getStageType());
        OrderPayment payment = orderPaymentMapper.selectByTradeNoForUpdate(tradeNo);
        if (stage == null || payment == null || !stage.getId().equals(payment.getPaymentStageId())) {
            throw new BaseException("订单支付阶段不匹配");
        }

        epayService.validateSuccessfulCallback(params, payment.getAmount());
        if (!method.equals(payment.getPaymentMethod())) throw new BaseException("支付方式不匹配");
        if (!order.getBuyerId().equals(payment.getPayerAccountId())) throw new BaseException("付款账号不匹配");
        recordCallback(params, tradeNo, providerTradeNo, tradeStatus);

        if ("SUCCESS".equals(payment.getStatus())) {
            if (!providerTradeNo.equals(payment.getProviderTradeNo())) {
                throw new BaseException("支付渠道流水号冲突");
            }
            return;
        }
        if (!"PENDING".equals(payment.getStatus())) throw new BaseException("支付流水状态不可入账");

        String expectedOrderStatus;
        String targetOrderStatus;
        if ("DEPOSIT".equals(payment.getStageType())) {
            expectedOrderStatus = OrderStatus.WAITING_DEPOSIT;
            targetOrderStatus = OrderStatus.DEPOSIT_PAID;
        } else if ("FINAL".equals(payment.getStageType())) {
            expectedOrderStatus = OrderStatus.DELIVERED;
            targetOrderStatus = OrderStatus.COMPLETED;
        } else {
            throw new BaseException("支付阶段无效");
        }
        if (!expectedOrderStatus.equals(order.getOrderStatus())) {
            throw new BaseException("订单当前状态不可入账");
        }

        if (orderPaymentMapper.updateStatusIfPending(tradeNo, providerTradeNo) != 1) {
            throw new BaseException("支付流水状态更新失败");
        }
        if (paymentStageMapper.updatePayStatusIfUnpaid(stage.getId()) != 1) {
            throw new BaseException("支付阶段状态更新失败");
        }
        if (orderMapper.updateStatusIfCurrent(order.getId(), expectedOrderStatus, targetOrderStatus) != 1) {
            throw new BaseException("订单状态更新失败");
        }

        if ("DEPOSIT".equals(payment.getStageType())) {
            notificationService.send(order.getSellerId(), "ORDER", "买家已支付定金", order.getId());
            notificationService.send(order.getBuyerId(), "ORDER", "定金支付成功，等待卖家交付", order.getId());
        } else {
            notificationService.send(order.getSellerId(), "ORDER", "买家已支付尾款，订单完成", order.getId());
            notificationService.send(order.getBuyerId(), "ORDER", "尾款支付成功，订单已完成", order.getId());
        }
        log.info("订单 Epay 支付完成: tradeNo={}, providerTradeNo={}, orderId={}, stage={}",
                tradeNo, providerTradeNo, order.getId(), payment.getStageType());
    }

    private void requirePayableOrderState(Order order, String stageType) {
        if ("DEPOSIT".equals(stageType) && !OrderStatus.WAITING_DEPOSIT.equals(order.getOrderStatus())) {
            throw new BaseException("订单当前状态不允许支付定金");
        }
        if ("FINAL".equals(stageType) && !OrderStatus.DELIVERED.equals(order.getOrderStatus())) {
            throw new BaseException("请先确认交付，再支付尾款");
        }
    }

    private void recordCallback(Map<String, String> params, String tradeNo,
                                String providerTradeNo, String tradeStatus) {
        OrderPaymentCallbackEvent event = new OrderPaymentCallbackEvent();
        event.setProvider("epay");
        event.setProviderEventId(providerTradeNo + ":" + tradeStatus);
        event.setTradeNo(tradeNo);
        event.setProviderTradeNo(providerTradeNo);
        event.setTradeStatus(tradeStatus);
        Map<String, String> safePayload = new LinkedHashMap<>();
        for (String name : List.of("pid", "trade_no", "out_trade_no", "type", "name",
                "money", "trade_status", "param")) {
            if (params.containsKey(name)) safePayload.put(name, params.get(name));
        }
        try {
            event.setPayload(objectMapper.writeValueAsString(safePayload));
        } catch (JsonProcessingException e) {
            throw new BaseException("支付回调记录序列化失败");
        }
        callbackEventMapper.insertIgnore(event);
    }

    private String required(Map<String, String> params, String name, int maxLength) {
        String value = params.get(name);
        if (value == null || value.isBlank()) throw new BaseException("支付回调缺少字段: " + name);
        if (value.length() > maxLength) throw new BaseException("支付回调字段过长: " + name);
        return value;
    }

    private String upper(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private String lower(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
}
