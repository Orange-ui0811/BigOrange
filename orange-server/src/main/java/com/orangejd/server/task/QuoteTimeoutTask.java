package com.orangejd.server.task;

import com.orangejd.common.constant.QuoteStatus;
import com.orangejd.pojo.entity.Quote;
import com.orangejd.server.service.QuoteTimeoutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时处理超时报价单
 */
@Component
@lombok.RequiredArgsConstructor
public class QuoteTimeoutTask {

    private final QuoteTimeoutService timeoutService;

    @Scheduled(cron = "0 */10 * * * ?") // 每10分钟执行一次
    public void processExpiredQuotes() {
        timeoutService.process();
    }
}
