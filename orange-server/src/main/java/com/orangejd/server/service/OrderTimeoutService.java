package com.orangejd.server.service;
import com.orangejd.common.constant.OrderStatus;
import com.orangejd.server.mapper.OrderMapper;
import org.springframework.stereotype.Service;
@Service
public class OrderTimeoutService {
    private final OrderMapper orders; private final NotificationService notifications;
    public OrderTimeoutService(OrderMapper orders, NotificationService notifications) { this.orders = orders; this.notifications = notifications; }
    public void process() {
        cancel(OrderStatus.DEPOSIT_PAID, 7, "已付定金超过7天未交付，系统自动取消");
        cancel(OrderStatus.DELIVERED, 7, "已交付超过7天未付尾款，系统自动取消");
    }
    private void cancel(String status, int days, String reason) {
        orders.selectTimeoutOrders(status, days).forEach(order -> {
            orders.updateStatus(order.getId(), OrderStatus.CANCELLED);
            notifications.send(order.getBuyerId(), "ORDER", "订单 #" + order.getId() + " " + reason, order.getId());
            notifications.send(order.getSellerId(), "ORDER", "订单 #" + order.getId() + " " + reason, order.getId());
        });
    }
}
