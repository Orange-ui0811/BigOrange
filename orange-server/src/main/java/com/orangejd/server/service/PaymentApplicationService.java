package com.orangejd.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.PaymentCallbackEvent;
import com.orangejd.pojo.entity.TopUp;
import com.orangejd.server.mapper.PaymentCallbackEventMapper;
import com.orangejd.server.mapper.TopUpMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns the payment state transition and Segments credit as one transaction. */
@Service
public class PaymentApplicationService {
    private static final Logger log = LoggerFactory.getLogger(PaymentApplicationService.class);

    private final EpayService epayService;
    private final TopUpMapper topUpMapper;
    private final PaymentCallbackEventMapper callbackEventMapper;
    private final QuotaService quotaService;
    private final ObjectMapper objectMapper;

    public PaymentApplicationService(EpayService epayService,
                                     TopUpMapper topUpMapper,
                                     PaymentCallbackEventMapper callbackEventMapper,
                                     QuotaService quotaService,
                                     ObjectMapper objectMapper) {
        this.epayService = epayService;
        this.topUpMapper = topUpMapper;
        this.callbackEventMapper = callbackEventMapper;
        this.quotaService = quotaService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void processEpayCallback(Map<String, String> params) {
        if (!epayService.verify(params)) throw new BaseException("支付回调验签失败");

        String tradeNo = required(params, "out_trade_no", 64);
        required(params, "trade_status", 32);
        TopUp topUp = topUpMapper.selectByTradeNoForUpdate(tradeNo);
        if (topUp == null) throw new BaseException("支付订单不存在");

        epayService.validateSuccessfulCallback(params, topUp);
        String providerTradeNo = required(params, "trade_no", 128);
        recordCallback(params, tradeNo, providerTradeNo);

        if ("success".equals(topUp.getStatus())) {
            if (!providerTradeNo.equals(topUp.getProviderTradeNo())) {
                throw new BaseException("支付订单渠道流水号冲突");
            }
            return;
        }
        if (!"pending".equals(topUp.getStatus())) throw new BaseException("支付订单状态不可入账");

        int changed = topUpMapper.updateStatusIfPending(tradeNo, "success", providerTradeNo);
        if (changed != 1) throw new BaseException("支付订单状态更新失败");

        quotaService.recharge(topUp.getAccountId(), topUp.getAmount(), "EPAY",
                "Epay 在线充值 " + topUp.getMoney() + " 元 → " + topUp.getAmount() + " segments");
        log.info("Epay 充值完成: tradeNo={}, providerTradeNo={}, accountId={}, amount={}",
                tradeNo, providerTradeNo, topUp.getAccountId(), topUp.getAmount());
    }

    private void recordCallback(Map<String, String> params, String tradeNo, String providerTradeNo) {
        PaymentCallbackEvent event = new PaymentCallbackEvent();
        event.setProvider("epay");
        event.setProviderEventId(providerTradeNo + ":" + params.get("trade_status"));
        event.setTradeNo(tradeNo);
        event.setProviderTradeNo(providerTradeNo);
        event.setTradeStatus(params.get("trade_status"));
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
}
