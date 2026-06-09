package com.orangejd.server.task;

import com.orangejd.common.constant.QuoteStatus;
import com.orangejd.pojo.entity.Quote;
import com.orangejd.server.mapper.QuoteMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时处理超时报价单
 */
@Component
public class QuoteTimeoutTask {

    @Autowired private QuoteMapper quoteMapper;

    @Scheduled(cron = "0 */10 * * * ?") // 每10分钟执行一次
    public void processExpiredQuotes() {
        List<Quote> expired = quoteMapper.selectExpired(QuoteStatus.PENDING_CONFIRM, LocalDateTime.now());
        for (Quote q : expired) {
            quoteMapper.updateStatus(q.getId(), QuoteStatus.EXPIRED);
        }
    }
}
