package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.Order;
import com.orangejd.pojo.vo.OrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper {
    int insert(Order order);
    Order selectById(@Param("id") Long id);
    Order selectByIdForUpdate(@Param("id") Long id);
    OrderVO selectVOById(@Param("id") Long id);
    List<OrderVO> selectByBuyerId(@Param("buyerId") Long buyerId, @Param("offset") int offset, @Param("limit") int limit);
    int countByBuyerId(@Param("buyerId") Long buyerId);
    List<OrderVO> selectBySellerId(@Param("sellerId") Long sellerId, @Param("offset") int offset, @Param("limit") int limit);
    int countBySellerId(@Param("sellerId") Long sellerId);
    int updateStatus(@Param("id") Long id, @Param("orderStatus") String orderStatus);
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("currentStatus") String currentStatus,
                              @Param("newStatus") String newStatus);
    int hideForBuyer(@Param("id") Long id, @Param("buyerId") Long buyerId);
    int hideForSeller(@Param("id") Long id, @Param("sellerId") Long sellerId);

    java.util.List<com.orangejd.pojo.vo.OrderVO> selectAll(@Param("offset") int offset, @Param("limit") int limit);

    int countAllOrders();
    int countBySource(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);

    java.util.List<com.orangejd.pojo.entity.Order> selectTimeoutOrders(@Param("status") String status, @Param("days") int days);
}
