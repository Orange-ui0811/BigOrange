package com.orangejd.server.task;

import com.orangejd.server.foundation.task.SystemTask;
import com.orangejd.server.foundation.task.SystemTaskHandler;
import com.orangejd.server.service.OpenSourceRefreshService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpenSourceRefreshTask implements SystemTaskHandler {
    public static final String TASK_TYPE = OpenSourceRefreshService.TASK_TYPE;
    private final OpenSourceRefreshService service;

    public OpenSourceRefreshTask(OpenSourceRefreshService service) {
        this.service = service;
    }

    @Scheduled(cron = "${orange.open-source.refresh-cron:0 30 3 * * 1}",
            zone = "${orange.open-source.refresh-zone:Asia/Shanghai}")
    public void scheduledFullRefresh() { service.scheduledFullRefresh(); }

    @Scheduled(cron = "${orange.open-source.verify-cron:0 30 4 * * *}",
            zone = "${orange.open-source.refresh-zone:Asia/Shanghai}")
    public void scheduledVerification() { service.scheduledVerification(); }

    public boolean triggerFullRefresh() { return service.triggerFullRefresh(); }
    public boolean triggerVerification() { return service.triggerVerification(); }
    public boolean triggerTranslation() { return service.triggerTranslation(); }
    public Map<String, Object> status() { return service.status(); }
    @Override public String taskType() { return service.taskType(); }
    @Override public Object execute(SystemTask task) throws Exception { return service.execute(task); }
}
