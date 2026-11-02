package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.PaymentCallbackEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentCallbackEventMapper {
    int insertIgnore(PaymentCallbackEvent event);
}
