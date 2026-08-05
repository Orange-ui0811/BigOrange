package com.orangejd.server.service;
import com.orangejd.common.exception.BaseException; import com.orangejd.server.mapper.AgentActionMapper; import org.springframework.util.StringUtils;
import java.time.LocalDateTime; import java.util.*;
final class AgentActionRepository {
 private final AgentActionMapper mapper; private final AgentActionSupport support;
 AgentActionRepository(AgentActionMapper mapper,AgentActionSupport support){this.mapper=mapper;this.support=support;}
 void recoverInterruptedTasks(){mapper.markInterruptedAfterRestart();}
Map<String, Object> status(Long accountId, Long credentialId, String taskId) {
        Map<String, Object> task = StringUtils.hasText(taskId)
                ? mapper.selectOwned(taskId, accountId, credentialId)
                : mapper.selectLatest(accountId, credentialId);
        if (task == null) throw new BaseException("暂无设备任务");
        if ("PREPARED".equals(task.get("status"))) {
            LocalDateTime expiresAt = support.toLocalDateTime(task.get("expiresAt"));
            if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
                mapper.expire(String.valueOf(task.get("id")));
                task = requireOwned(accountId, credentialId, String.valueOf(task.get("id")));
            }
        }
        return response(task);
    }

Map<String, Object> cancel(Long accountId, Long credentialId, String taskId) {
        if (mapper.cancel(taskId, accountId, credentialId) != 1) {
            throw new BaseException("任务已经开始、结束或不存在，当前不能取消");
        }
        return response(requireOwned(accountId, credentialId, taskId));
    }

List<Map<String, Object>> history(Long accountId, int limit) {
        List<Map<String, Object>> tasks = mapper.selectByAccount(
                accountId, Math.max(1, Math.min(limit, 100)));
        tasks.forEach(task -> task.put("result", support.parseNullable(task.remove("resultJson"))));
        return tasks;
    }

void deleteHistory(Long accountId, String taskId) {
        if (!StringUtils.hasText(taskId)
                || mapper.deleteTerminalByAccount(taskId, accountId) != 1) {
            throw new BaseException("任务不存在、尚未结束或无权删除");
        }
    }

void inheritPreviousPlanPhase(Long accountId, Long credentialId, int phase,
                                          Map<String, Object> parameters) {
        boolean hasSummary = StringUtils.hasText(support.text(parameters.get("summary"), 10_000));
        boolean hasPrevious = StringUtils.hasText(support.text(parameters.get("prevContents"), 30_000));
        if (hasSummary && hasPrevious) return;

        Map<String, Object> previous = mapper.selectLatestSucceeded(
                accountId, credentialId, "plan.phase.generate");
        if (previous == null) throw new BaseException("请先生成方案设计第 " + (phase - 1) + " 阶段");
        Map<String, Object> previousResult = support.parseMap(previous.get("resultJson"));
        Number previousPhase = support.number(previousResult.get("phase"), "上一阶段方案记录无效");
        if (previousPhase.intValue() != phase - 1) {
            throw new BaseException("请先生成方案设计第 " + (phase - 1) + " 阶段");
        }
        Map<String, Object> previousParameters = support.parseMap(previous.get("parametersJson"));
        if (!hasSummary) parameters.put("summary", previousParameters.get("summary"));
        if (!hasPrevious) parameters.put("prevContents", previousResult.get("content"));
        if (!parameters.containsKey("mode") && previousParameters.containsKey("mode")) {
            parameters.put("mode", previousParameters.get("mode"));
        }
    }

Map<String, Object> createPrepared(Long accountId, Long credentialId, String action,
                                                Map<String, Object> parameters, String summary,
                                                int cost, long balance, String spoken) {
        Map<String, Object> task = baseTask(accountId, credentialId, action, parameters,
                summary, "PREPARED", cost, balance, spoken);
        task.put("expiresAt", LocalDateTime.now().plusMinutes(5));
        mapper.insert(task);
        return response(requireOwned(accountId, credentialId, String.valueOf(task.get("id"))));
    }

Map<String, Object> createCompleted(Long accountId, Long credentialId, String action,
                                                 Map<String, Object> parameters, String summary,
                                                 long balance, Object result, String spoken) {
        Map<String, Object> task = baseTask(accountId, credentialId, action, parameters,
                summary, "SUCCEEDED", 0, balance, spoken);
        task.put("resultJson", support.json(result));
        mapper.insert(task);
        return response(requireOwned(accountId, credentialId, String.valueOf(task.get("id"))));
    }

Map<String, Object> baseTask(Long accountId, Long credentialId, String action,
                                         Map<String, Object> parameters, String summary,
                                         String status, int cost, long balance, String spoken) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", UUID.randomUUID().toString());
        task.put("accountId", accountId);
        task.put("credentialId", credentialId);
        task.put("actionKey", action);
        task.put("parametersJson", support.json(parameters));
        task.put("actionSummary", summary);
        task.put("status", status);
        task.put("quotaCost", cost);
        task.put("balance", balance);
        task.put("resultJson", null);
        task.put("spokenSummary", spoken);
        task.put("expiresAt", null);
        return task;
    }

Map<String, Object> response(Map<String, Object> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supported", true);
        result.put("taskId", raw.get("id"));
        result.put("actionKey", raw.get("actionKey"));
        result.put("actionSummary", raw.get("actionSummary"));
        result.put("status", raw.get("status"));
        result.put("quotaCost", raw.get("quotaCost"));
        result.put("balance", raw.get("balance"));
        result.put("needsConfirmation", "PREPARED".equals(raw.get("status")));
        result.put("confirmationId", "PREPARED".equals(raw.get("status")) ? raw.get("id") : null);
        result.put("expiresAt", raw.get("expiresAt"));
        result.put("spokenMessage", raw.get("spokenSummary"));
        result.put("errorCode", raw.get("errorCode"));
        result.put("result", support.parseNullable(raw.get("resultJson")));
        return result;
    }

Map<String, Object> requireOwned(Long accountId, Long credentialId, String taskId) {
        Map<String, Object> task = mapper.selectOwned(taskId, accountId, credentialId);
        if (task == null) throw new BaseException("设备任务不存在");
        return task;
    }
}

