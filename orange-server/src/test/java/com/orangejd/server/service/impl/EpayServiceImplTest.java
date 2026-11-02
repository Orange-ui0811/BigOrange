package com.orangejd.server.service.impl;

import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.TopUp;
import com.orangejd.server.config.EpayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EpayServiceImplTest {
    private static final String KEY = "test-secret";
    private EpayServiceImpl service;

    @BeforeEach
    void setUp() {
        EpayProperties props = new EpayProperties();
        props.setPid("merchant-1");
        props.setKey(KEY);
        props.setGateway("https://pay.example.test");
        props.setNotifyUrl("https://api.example.test/api/epay/notify");
        props.setReturnUrl("https://example.test/quota");
        service = new EpayServiceImpl(props);
    }

    @Test
    void verifiesSignatureAndRejectsTampering() throws Exception {
        Map<String, String> params = validCallback();
        params.put("sign", sign(params));
        assertTrue(service.verify(params));

        params.put("money", "0.01");
        assertFalse(service.verify(params));
    }

    @Test
    void validatesMerchantStatusAndAmount() {
        TopUp topUp = new TopUp();
        topUp.setMoney(new BigDecimal("10.00"));
        Map<String, String> params = validCallback();
        assertDoesNotThrow(() -> service.validateSuccessfulCallback(params, topUp));

        params.put("trade_status", "WAIT_BUYER_PAY");
        assertThrows(BaseException.class, () -> service.validateSuccessfulCallback(params, topUp));
    }

    private Map<String, String> validCallback() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", "merchant-1");
        params.put("out_trade_no", "EPAY_1_test");
        params.put("trade_no", "provider-10001");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("money", "10.00");
        return params;
    }

    private String sign(Map<String, String> params) throws Exception {
        List<String> keys = new ArrayList<>(params.keySet());
        keys.remove("sign");
        keys.remove("sign_type");
        keys.sort(String::compareTo);
        StringBuilder data = new StringBuilder();
        for (String key : keys) {
            String value = params.get(key);
            if (value != null && !value.isEmpty()) data.append(key).append('=').append(value).append('&');
        }
        data.append(KEY);
        byte[] digest = MessageDigest.getInstance("MD5").digest(data.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte b : digest) result.append(String.format("%02x", b));
        return result.toString();
    }
}
