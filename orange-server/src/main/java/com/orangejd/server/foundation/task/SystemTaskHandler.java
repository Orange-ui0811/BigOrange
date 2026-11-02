package com.orangejd.server.foundation.task;

public interface SystemTaskHandler {
    String taskType();

    default boolean supports(String candidateTaskType) {
        return taskType().equals(candidateTaskType);
    }

    Object execute(SystemTask task) throws Exception;
}
