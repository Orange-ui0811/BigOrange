package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.Delivery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeliveryMapper {
    int insert(Delivery delivery);
    List<Delivery> selectByOrderId(@Param("orderId") Long orderId);
    Delivery selectById(@Param("id") Long id);
}
