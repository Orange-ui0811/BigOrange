package com.orangejd.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.constant.OrderStatus;
import com.orangejd.common.constant.PayStatus;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.dto.OrderPaymentCreateDTO;
import com.orangejd.pojo.entity.Order;
import com.orangejd.pojo.entity.OrderPayment;
import com.orangejd.pojo.entity.PaymentStage;
import com.orangejd.server.config.EpayProperties;
import com.orangejd.server.mapper.OrderMapper;
import com.orangejd.server.mapper.OrderPaymentCallbackEventMapper;
import com.orangejd.server.mapper.OrderPaymentMapper;
import com.orangejd.server.mapper.PaymentStageMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderPaymentApplicationServiceTest {
    private EpayProperties properties;
    private EpayService epayService;
    private OrderMapper orderMapper;
    private PaymentStageMapper stageMapper;
    private OrderPaymentMapper paymentMapper;
    private OrderPaymentCallbackEventMapper eventMapper;
    private NotificationService notificationService;
    private RateLimitService rateLimitService;
    private OrderPaymentApplicationService service;

    @BeforeEach
    void setUp() {
        properties = new EpayProperties();
        properties.setPid("merchant-1");
        properties.setKey("payment-secret-1234567890");
        properties.setGateway("https://pay.example.test/submit.php");
        properties.setNotifyUrl("https://app.example.test/api/epay/notify");
        properties.setReturnUrl("https://app.example.test/me/orders");
        properties.setOrderPaymentsEnabled(true);
        epayService = mock(EpayService.class);
        orderMapper = mock(OrderMapper.class);
        stageMapper = mock(PaymentStageMapper.class);
        paymentMapper = mock(OrderPaymentMapper.class);
        eventMapper = mock(OrderPaymentCallbackEventMapper.class);
        notificationService = mock(NotificationService.class);
        rateLimitService = mock(RateLimitService.class);
        service = new OrderPaymentApplicationService(properties, epayService, orderMapper, stageMapper,
                paymentMapper, eventMapper, notificationService, rateLimitService, new ObjectMapper());
        BaseContext.setCurrentId(7L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void createsPaymentFromLockedServerAmount() {
        Order order = order(OrderStatus.WAITING_DEPOSIT);
        PaymentStage stage = stage("DEPOSIT", "19.90", PayStatus.UNPAID);
        when(rateLimitService.allow(anyString(), eq(10), eq(300))).thenReturn(true);
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);
        when(stageMapper.selectByOrderAndTypeForUpdate(10L, "DEPOSIT")).thenReturn(stage);
        when(paymentMapper.selectByStageForUpdate(100L)).thenReturn(null);
        when(epayService.createPayment(anyString(), eq(new BigDecimal("19.90")), eq("wxpay"),
                eq("OrangeJD 订单#10 定金"))).thenReturn("https://pay.example.test/checkout");

        Map<String, String> result = service.create(dto(10L, "DEPOSIT", "wxpay"));

        assertEquals("https://pay.example.test/checkout", result.get("payUrl"));
        verify(paymentMapper).insert(argThat(p -> p.getTradeNo().startsWith("ORD_")
                && new BigDecimal("19.90").compareTo(p.getAmount()) == 0
                && p.getPayerAccountId().equals(7L)));
    }

    @Test
    void refusesCreateWhenOrderPaymentsAreDisabled() {
        properties.setOrderPaymentsEnabled(false);
        assertThrows(BaseException.class, () -> service.create(dto(10L, "DEPOSIT", "wxpay")));
        verifyNoInteractions(orderMapper, stageMapper, paymentMapper);
    }

    @Test
    void appliesVerifiedDepositCallbackExactlyOnce() {
        Map<String, String> callback = callback();
        Order order = order(OrderStatus.WAITING_DEPOSIT);
        PaymentStage stage = stage("DEPOSIT", "19.90", PayStatus.UNPAID);
        OrderPayment payment = payment("PENDING", null);
        when(epayService.verify(callback)).thenReturn(true);
        when(paymentMapper.selectByTradeNo("ORD_local")).thenReturn(payment);
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);
        when(stageMapper.selectByOrderAndTypeForUpdate(10L, "DEPOSIT")).thenReturn(stage);
        when(paymentMapper.selectByTradeNoForUpdate("ORD_local")).thenReturn(payment);
        when(paymentMapper.updateStatusIfPending("ORD_local", "provider-1")).thenReturn(1);
        when(stageMapper.updatePayStatusIfUnpaid(100L)).thenReturn(1);
        when(orderMapper.updateStatusIfCurrent(10L, OrderStatus.WAITING_DEPOSIT,
                OrderStatus.DEPOSIT_PAID)).thenReturn(1);

        service.processEpayCallback(callback);

        verify(epayService).validateSuccessfulCallback(callback, new BigDecimal("19.90"));
        verify(eventMapper).insertIgnore(any());
        verify(notificationService, times(2)).send(anyLong(), eq("ORDER"), anyString(), eq(10L));
    }

    @Test
    void duplicateSuccessfulCallbackDoesNotAdvanceAgain() {
        Map<String, String> callback = callback();
        Order order = order(OrderStatus.DEPOSIT_PAID);
        PaymentStage stage = stage("DEPOSIT", "19.90", PayStatus.PAID);
        OrderPayment payment = payment("SUCCESS", "provider-1");
        when(epayService.verify(callback)).thenReturn(true);
        when(paymentMapper.selectByTradeNo("ORD_local")).thenReturn(payment);
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);
        when(stageMapper.selectByOrderAndTypeForUpdate(10L, "DEPOSIT")).thenReturn(stage);
        when(paymentMapper.selectByTradeNoForUpdate("ORD_local")).thenReturn(payment);

        service.processEpayCallback(callback);

        verify(paymentMapper, never()).updateStatusIfPending(anyString(), anyString());
        verify(stageMapper, never()).updatePayStatusIfUnpaid(anyLong());
        verify(orderMapper, never()).updateStatusIfCurrent(anyLong(), anyString(), anyString());
        verifyNoInteractions(notificationService);
    }

    private OrderPaymentCreateDTO dto(Long orderId, String stageType, String method) {
        OrderPaymentCreateDTO dto = new OrderPaymentCreateDTO();
        dto.setOrderId(orderId);
        dto.setStageType(stageType);
        dto.setMethod(method);
        return dto;
    }

    private Order order(String status) {
        Order order = new Order();
        order.setId(10L);
        order.setBuyerId(7L);
        order.setSellerId(8L);
        order.setOrderStatus(status);
        return order;
    }

    private PaymentStage stage(String type, String amount, String status) {
        PaymentStage stage = new PaymentStage();
        stage.setId(100L);
        stage.setOrderId(10L);
        stage.setStageType(type);
        stage.setAmount(new BigDecimal(amount));
        stage.setPayStatus(status);
        return stage;
    }

    private OrderPayment payment(String status, String providerTradeNo) {
        OrderPayment payment = new OrderPayment();
        payment.setId(200L);
        payment.setTradeNo("ORD_local");
        payment.setOrderId(10L);
        payment.setPaymentStageId(100L);
        payment.setPayerAccountId(7L);
        payment.setStageType("DEPOSIT");
        payment.setAmount(new BigDecimal("19.90"));
        payment.setPaymentMethod("wxpay");
        payment.setPaymentProvider("epay");
        payment.setProviderTradeNo(providerTradeNo);
        payment.setStatus(status);
        return payment;
    }

    private Map<String, String> callback() {
        Map<String, String> callback = new LinkedHashMap<>();
        callback.put("pid", "merchant-1");
        callback.put("type", "wxpay");
        callback.put("out_trade_no", "ORD_local");
        callback.put("trade_no", "provider-1");
        callback.put("trade_status", "TRADE_SUCCESS");
        callback.put("money", "19.90");
        callback.put("sign", "signed");
        callback.put("sign_type", "MD5");
        return callback;
    }
}
