package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.OrderPayment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderPaymentMapper {
    int insert(OrderPayment payment);
    OrderPayment selectByTradeNo(@Param("tradeNo") String tradeNo);
    OrderPayment selectByTradeNoForUpdate(@Param("tradeNo") String tradeNo);
    OrderPayment selectByStageForUpdate(@Param("paymentStageId") Long paymentStageId);
    int updateStatusIfPending(@Param("tradeNo") String tradeNo,
                              @Param("providerTradeNo") String providerTradeNo);
}
