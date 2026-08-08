package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.QuotaOperation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QuotaOperationMapper {
    int insert(QuotaOperation operation);
    QuotaOperation selectByRequestIdForUpdate(@Param("requestId") String requestId);
    int attachDebitLog(@Param("id") Long id, @Param("debitLogId") Long debitLogId);
    int markRefunded(@Param("id") Long id, @Param("refundLogId") Long refundLogId);
}
