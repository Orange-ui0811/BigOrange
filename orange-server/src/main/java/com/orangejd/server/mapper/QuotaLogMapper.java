package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.QuotaLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuotaLogMapper {
    int insert(QuotaLog record);
    List<QuotaLog> selectByAccountId(@Param("accountId") Long accountId, @Param("limit") int limit);
    int existsByRequestId(@Param("requestId") String requestId);
    QuotaLog selectByRequestIdForUpdate(@Param("requestId") String requestId);
}
