package com.orangejd.server.service;

import com.orangejd.pojo.entity.TopUp;
import java.math.BigDecimal;
import java.util.Map;

public interface EpayService {

    /** 创建支付 → 返回支付链接（用户扫码用） */
    String createPayment(String tradeNo, int amount, String method);

    /** 创建按人民币金额计价的支付链接。金额和商品名只能由服务端业务数据产生。 */
    String createPayment(String tradeNo, BigDecimal money, String method, String subject);

    /** 验签回调参数 */
    boolean verify(Map<String, String> params);

    /** Validate provider identity, successful status and immutable order amount. */
    void validateSuccessfulCallback(Map<String, String> params, TopUp topUp);

    /** Validate provider identity, successful status and immutable expected money. */
    void validateSuccessfulCallback(Map<String, String> params, BigDecimal expectedMoney);
}
