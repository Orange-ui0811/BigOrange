package com.orangejd.server.service;
import com.fasterxml.jackson.core.type.TypeReference; import com.fasterxml.jackson.databind.ObjectMapper; import com.orangejd.common.exception.BaseException; import org.springframework.util.StringUtils;
import java.time.LocalDateTime; import java.util.*;
final class AgentActionSupport {
static final List<String> ALLOWED_ACTIONS = List.of(
            "quota.balance.read", "launch_check.project.list",
            "launch_check.project.create", "launch_check.stage.generate",
            "open_source.issue.list", "open_source.issue.detail",
            "open_source.issue.job_match", "open_source.issue.guide",
            "trend.analysis.generate", "trend.profile.generate", "plan.phase.generate",
            "agent.task.list", "notification.unread.read", "leaderboard.github.list");
 private final ObjectMapper objectMapper;
 AgentActionSupport(ObjectMapper objectMapper){this.objectMapper=objectMapper;}
Map<String, Object> parameters(Object value) {
        if (value == null) return new LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> map)) throw new BaseException("动作参数格式不正确");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

String summarizeProjects(List<Map<String, Object>> projects) {
        if (projects.isEmpty()) return "你还没有上线体检项目。";
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(3, projects.size()); i++)
            names.add("编号 " + projects.get(i).get("id") + " “"
                    + projects.get(i).get("projectName") + "”");
        return "你最近的上线体检项目有：" + String.join("、", names) + "。";
    }

String summarizeIssues(List<Map<String, Object>> issues) {
        if (issues.isEmpty()) return "当前没有符合条件的可参与 Issue。";
        List<String> items = new ArrayList<>();
        for (int i = 0; i < Math.min(5, issues.size()); i++) {
            Map<String, Object> issue = issues.get(i);
            items.add("编号 " + issue.get("id") + " “" + issue.get("titleZh") + "”");
        }
        return "找到这些开源 Issue：" + String.join("、", items) + "。你可以继续查询某个编号的详情。";
    }

String summarizeTasks(List<Map<String, Object>> tasks) {
        if (tasks.isEmpty()) return "你还没有 OrangeVC 语音任务记录。";
        List<String> items = new ArrayList<>();
        for (int i = 0; i < Math.min(5, tasks.size()); i++) {
            Map<String, Object> task = tasks.get(i);
            items.add(String.valueOf(task.getOrDefault("actionSummary", "未命名任务"))
                    + "，状态" + task.getOrDefault("status", "未知"));
        }
        return "你最近的语音任务有：" + String.join("；", items) + "。";
    }

String nullableText(Object value, int maxLength) {
        String result = text(value, maxLength);
        return StringUtils.hasText(result) ? result : null;
    }

Integer optionalInt(Object value, int fallback) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) return fallback;
        return number(value, "更新时间范围无效").intValue();
    }

String abbreviate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

boolean hasScope(String scopes, String action) {
        return StringUtils.hasText(scopes) && List.of(scopes.split(",")).contains(action);
    }

String text(Object value, int maxLength) {
        String result = value == null ? "" : String.valueOf(value).trim();
        if (result.length() > maxLength) throw new BaseException("输入内容过长");
        return result;
    }

Number number(Object value, String message) {
        if (value instanceof Number number) return number;
        try { return Long.parseLong(String.valueOf(value)); }
        catch (Exception e) { throw new BaseException(message); }
    }

String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("动作数据序列化失败", e); }
    }

Map<String, Object> parseMap(Object value) {
        Object parsed = parseNullable(value);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

Object parseNullable(Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) return value;
        try {
            if (value instanceof byte[] bytes) {
                return objectMapper.readValue(bytes, new TypeReference<Object>() {});
            }
            return objectMapper.readValue(String.valueOf(value), new TypeReference<Object>() {});
        }
        catch (Exception e) { return null; }
    }

LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime time) return time;
        if (value == null) return null;
        try { return LocalDateTime.parse(String.valueOf(value)); }
        catch (Exception e) { return null; }
    }
}

