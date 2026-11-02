package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.OrderPaymentCallbackEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderPaymentCallbackEventMapper {
    int insertIgnore(OrderPaymentCallbackEvent event);
}
