package com.orangejd.server.task;

import com.orangejd.common.constant.OrderStatus;
import com.orangejd.common.context.BaseContext;
import com.orangejd.pojo.entity.Order;
import com.orangejd.server.service.OrderTimeoutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 定时处理超时订单
 */
@Component
@lombok.RequiredArgsConstructor
public class OrderTimeoutTask {

    private final OrderTimeoutService timeoutService;

    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点
    public void processTimeoutOrders() {
        timeoutService.process();
    }
}
