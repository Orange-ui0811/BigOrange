package com.orangejd.server.task;

import com.orangejd.server.mapper.NotificationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时清理 30 天前的已读通知
 */
@Component
public class NotificationCleanupTask {

    @Autowired private NotificationMapper notificationMapper;

    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点
    public void cleanup() {
        notificationMapper.deleteOldRead(30);
    }
}
