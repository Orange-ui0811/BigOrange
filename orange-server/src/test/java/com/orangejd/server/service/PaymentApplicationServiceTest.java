package com.orangejd.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.pojo.entity.TopUp;
import com.orangejd.server.mapper.PaymentCallbackEventMapper;
import com.orangejd.server.mapper.TopUpMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentApplicationServiceTest {
    private EpayService epayService;
    private TopUpMapper topUpMapper;
    private PaymentCallbackEventMapper eventMapper;
    private QuotaService quotaService;
    private PaymentApplicationService service;

    @BeforeEach
    void setUp() {
        epayService = mock(EpayService.class);
        topUpMapper = mock(TopUpMapper.class);
        eventMapper = mock(PaymentCallbackEventMapper.class);
        quotaService = mock(QuotaService.class);
        service = new PaymentApplicationService(epayService, topUpMapper, eventMapper, quotaService, new ObjectMapper());
    }

    @Test
    void creditsExactlyOnceAfterConditionalStateChange() {
        Map<String, String> callback = callback();
        TopUp topUp = topUp("pending", null);
        when(epayService.verify(callback)).thenReturn(true);
        when(topUpMapper.selectByTradeNoForUpdate("local-1")).thenReturn(topUp);
        when(topUpMapper.updateStatusIfPending("local-1", "success", "provider-1")).thenReturn(1);

        service.processEpayCallback(callback);

        verify(epayService).validateSuccessfulCallback(callback, topUp);
        verify(eventMapper).insertIgnore(any());
        verify(quotaService).recharge(7L, 1000, "EPAY", "Epay 在线充值 10.00 元 → 1000 segments");
    }

    @Test
    void duplicateSuccessfulCallbackDoesNotCreditAgain() {
        Map<String, String> callback = callback();
        when(epayService.verify(callback)).thenReturn(true);
        when(topUpMapper.selectByTradeNoForUpdate("local-1")).thenReturn(topUp("success", "provider-1"));

        service.processEpayCallback(callback);

        verify(topUpMapper, never()).updateStatusIfPending(anyString(), anyString(), anyString());
        verify(quotaService, never()).recharge(anyLong(), anyInt(), anyString(), anyString());
    }

    @Test
    void rejectsOversizedProviderIdentifiersBeforeDatabaseWrite() {
        Map<String, String> callback = callback();
        callback.put("out_trade_no", "x".repeat(65));
        when(epayService.verify(callback)).thenReturn(true);

        assertThrows(RuntimeException.class, () -> service.processEpayCallback(callback));
        verify(topUpMapper, never()).selectByTradeNoForUpdate(anyString());
        verify(eventMapper, never()).insertIgnore(any());
    }

    private Map<String, String> callback() {
        Map<String, String> callback = new LinkedHashMap<>();
        callback.put("out_trade_no", "local-1");
        callback.put("trade_no", "provider-1");
        callback.put("trade_status", "TRADE_SUCCESS");
        return callback;
    }

    private TopUp topUp(String status, String providerTradeNo) {
        TopUp topUp = new TopUp();
        topUp.setAccountId(7L);
        topUp.setAmount(1000);
        topUp.setMoney(new BigDecimal("10.00"));
        topUp.setStatus(status);
        topUp.setProviderTradeNo(providerTradeNo);
        return topUp;
    }
}
