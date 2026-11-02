package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.PaymentStage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PaymentStageMapper {
    int insert(PaymentStage stage);
    List<PaymentStage> selectByOrderId(@Param("orderId") Long orderId);
    PaymentStage selectByOrderAndTypeForUpdate(@Param("orderId") Long orderId,
                                               @Param("stageType") String stageType);
    int updatePayStatus(@Param("id") Long id, @Param("payStatus") String payStatus);
    int updatePayStatusIfUnpaid(@Param("id") Long id);
}
