package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.TopUp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TopUpMapper {
    int insert(TopUp record);
    TopUp selectByTradeNo(@Param("tradeNo") String tradeNo);
    TopUp selectByTradeNoForUpdate(@Param("tradeNo") String tradeNo);
    int updateStatusIfPending(@Param("tradeNo") String tradeNo,
                              @Param("status") String status,
                              @Param("providerTradeNo") String providerTradeNo);
    List<TopUp> selectPending(@Param("accountId") Long accountId);
    List<TopUp> selectAll(@Param("offset") int offset, @Param("limit") int limit);
}
