package com.orangejd.server.task;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SystemMaintenanceTask {

    @Scheduled(cron = "0 0/30 * * * ?")
    public void heartbeat() {
        // Place time-driven business logic here.
    }
}

