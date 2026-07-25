package com.orangejd.server.foundation.task;

public interface SystemTaskHandler {
    String taskType();

    Object execute(SystemTask task) throws Exception;
}
