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
    int updateStatus(@Param("tradeNo") String tradeNo, @Param("status") String status);
    List<TopUp> selectPending(@Param("accountId") Long accountId);
    List<TopUp> selectAll(@Param("offset") int offset, @Param("limit") int limit);
}
