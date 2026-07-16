package com.orangejd.server.service;

import com.orangejd.pojo.entity.TopUp;
import java.util.Map;

public interface EpayService {

    /** 创建支付 → 返回支付链接（用户扫码用） */
    String createPayment(String tradeNo, int amount, String method);

    /** 验签回调参数 */
    boolean verify(Map<String, String> params);

    /** Validate provider identity, successful status and immutable order amount. */
    void validateSuccessfulCallback(Map<String, String> params, TopUp topUp);
}
