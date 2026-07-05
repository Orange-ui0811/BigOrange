package com.orangejd.server.service.impl;

import com.orangejd.server.config.EpayProperties;
import com.orangejd.server.service.EpayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class EpayServiceImpl implements EpayService {

    private static final Logger log = LoggerFactory.getLogger(EpayServiceImpl.class);
    private final EpayProperties props;

    public EpayServiceImpl(EpayProperties props) {
        this.props = props;
    }

    @Override
    public String createPayment(String tradeNo, int amount, String method) {
        // 100 segments = 1 元
        BigDecimal money = new BigDecimal(amount).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", props.getPid());
        params.put("type", method);
        params.put("out_trade_no", tradeNo);
        params.put("notify_url", props.getNotifyUrl());
        params.put("return_url", props.getReturnUrl());
        params.put("name", "OrangeJD 配额充值");
        params.put("money", money.toPlainString());
        params.put("param", tradeNo); // 自定义参数，回调时原样返回

        // 加签
        String sign = sign(params, props.getKey());
        params.put("sign", sign);
        params.put("sign_type", "MD5");

        // 构建支付链接
        StringBuilder sb = new StringBuilder(props.getGateway());
        sb.append(props.getGateway().contains("?") ? "&" : "?");
        for (Map.Entry<String, String> e : params.entrySet()) {
            sb.append(e.getKey()).append("=").append(urlEncode(e.getValue())).append("&");
        }
        String payUrl = sb.substring(0, sb.length() - 1);
        log.info("Epay 支付链接生成: tradeNo={}, amount={}, money={}", tradeNo, amount, money.toPlainString());
        return payUrl;
    }

    @Override
    public boolean verify(Map<String, String> params) {
        String receivedSign = params.get("sign");
        if (receivedSign == null) return false;

        // 除了 sign 和 sign_type 之外的参数参与签名
        Map<String, String> toSign = new LinkedHashMap<>(params);
        toSign.remove("sign");
        toSign.remove("sign_type");

        String computed = sign(toSign, props.getKey());
        boolean ok = receivedSign.equals(computed);
        if (!ok) log.warn("Epay 签名验证失败: received={}, computed={}", receivedSign, computed);
        return ok;
    }

    /** MD5 签名 */
    private String sign(Map<String, String> params, String key) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            String v = params.get(k);
            if (v != null && !v.isEmpty()) {
                sb.append(k).append("=").append(v).append("&");
            }
        }
        sb.append(key);
        return md5(sb.toString());
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 error", e);
        }
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
