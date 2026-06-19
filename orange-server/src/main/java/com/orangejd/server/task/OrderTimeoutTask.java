package com.orangejd.server.task;

import com.orangejd.common.constant.OrderStatus;
import com.orangejd.common.context.BaseContext;
import com.orangejd.pojo.entity.Order;
import com.orangejd.server.mapper.OrderMapper;
import com.orangejd.server.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 定时处理超时订单
 */
@Component
public class OrderTimeoutTask {

    @Autowired private OrderMapper orderMapper;
    @Autowired private NotificationService notificationService;

    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点
    public void processTimeoutOrders() {
        // 1. DEPOSIT_PAID 超 7 天未交付 → 自动取消
        cancelTimeoutOrders(OrderStatus.DEPOSIT_PAID, 7, "已付定金超过7天未交付，系统自动取消");

        // 2. DELIVERED 超 7 天未付尾款 → 自动取消
        cancelTimeoutOrders(OrderStatus.DELIVERED, 7, "已交付超过7天未付尾款，系统自动取消");
    }

    private void cancelTimeoutOrders(String status, int days, String reason) {
        List<Order> orders = orderMapper.selectTimeoutOrders(status, days);
        for (Order o : orders) {
            orderMapper.updateStatus(o.getId(), OrderStatus.CANCELLED);
            notificationService.send(o.getBuyerId(), "ORDER",
                    "订单 #" + o.getId() + " " + reason, o.getId());
            notificationService.send(o.getSellerId(), "ORDER",
                    "订单 #" + o.getId() + " " + reason, o.getId());
        }
    }
}
