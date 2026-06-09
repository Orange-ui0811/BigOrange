package com.orangejd.server.task;

import com.orangejd.common.constant.OrderStatus;
import com.orangejd.pojo.entity.Order;
import com.orangejd.server.mapper.OrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时处理超时订单
 */
@Component
public class OrderTimeoutTask {

    @Autowired private OrderMapper orderMapper;

    @Scheduled(cron = "0 */5 * * * ?") // 每5分钟执行
    public void processTimeoutOrders() {
        // Phase 7: 扫描 WAITING_DEPOSIT 超过7天或 DELIVERED 超过7天的订单自动取消
        // 当前空实现，Phase 7 完善
    }
}
