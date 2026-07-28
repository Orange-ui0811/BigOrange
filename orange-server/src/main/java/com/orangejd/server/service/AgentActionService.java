package com.orangejd.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.context.BaseContext;
import com.orangejd.server.mapper.AgentActionMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AgentActionService {
    private static final List<String> ALLOWED_ACTIONS = List.of(
            "quota.balance.read", "launch_check.project.list",
            "launch_check.project.create", "launch_check.stage.generate",
            "open_source.issue.list", "open_source.issue.detail",
            "open_source.issue.job_match", "open_source.issue.guide",
            "trend.analysis.generate", "trend.profile.generate", "plan.phase.generate",
            "agent.task.list", "notification.unread.read", "leaderboard.github.list");

    private final AgentActionMapper mapper;
    private final LaunchCheckService launchCheckService;
    private final QuotaService quotaService;
    private final PricingService pricingService;
    private final OpenSourceContributionService openSourceService;
    private final TrendAnalysisService trendAnalysisService;
    private final TrendProfileAnalysisService trendProfileAnalysisService;
    private final PlanGenerationService planGenerationService;
    private final NotificationService notificationService;
    private final LeaderboardService leaderboardService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    public AgentActionService(AgentActionMapper mapper, LaunchCheckService launchCheckService,
                              QuotaService quotaService, PricingService pricingService,
                              OpenSourceContributionService openSourceService,
                              TrendAnalysisService trendAnalysisService,
                              TrendProfileAnalysisService trendProfileAnalysisService,
                              PlanGenerationService planGenerationService,
                              NotificationService notificationService,
                              LeaderboardService leaderboardService,
                              ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.launchCheckService = launchCheckService;
        this.quotaService = quotaService;
        this.pricingService = pricingService;
        this.openSourceService = openSourceService;
        this.trendAnalysisService = trendAnalysisService;
        this.trendProfileAnalysisService = trendProfileAnalysisService;
        this.planGenerationService = planGenerationService;
        this.notificationService = notificationService;
        this.leaderboardService = leaderboardService;
        this.objectMapper = objectMapper;
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "agent-action-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PostConstruct
    public void recoverInterruptedTasks() {
        mapper.markInterruptedAfterRestart();
    }

    public Map<String, Object> prepare(Long accountId, Long credentialId,
                                       String scopes, Map<String, Object> body) {
        String action = text(body.get("action"), 64);
        if (!ALLOWED_ACTIONS.contains(action) || !hasScope(scopes, action)) {
            throw new BaseException("设备没有执行该动作的权限");
        }
        Map<String, Object> parameters = parameters(body.get("parameters"));
        long balance = quotaService.getBalance(accountId);
        return switch (action) {
            case "quota.balance.read" -> createCompleted(accountId, credentialId, action,
                    parameters, "查询 OrangeVC 配额", balance, Map.of("balance", balance),
                    "你当前有 " + balance + " 点 OrangeVC 配额。");
            case "agent.task.list" -> {
                int limit = Math.max(1, Math.min(optionalInt(parameters.get("limit"), 5), 10));
                List<Map<String, Object>> tasks = history(accountId, limit);
                yield createCompleted(accountId, credentialId, action, parameters,
                        "查询最近语音任务", balance, Map.of("tasks", tasks),
                        summarizeTasks(tasks));
            }
            case "notification.unread.read" -> {
                int unread = notificationService.unreadCount();
                yield createCompleted(accountId, credentialId, action, parameters,
                        "查询未读通知", balance, Map.of("unreadCount", unread),
                        unread == 0
                                ? "你当前没有未读通知。"
                                : "你当前有 " + unread + " 条 OrangeVC 未读通知。");
            }
            case "leaderboard.github.list" -> {
                String period = text(parameters.getOrDefault("period", "week"), 16);
                if (!List.of("week", "new", "all").contains(period)) {
                    throw new BaseException("GitHub 榜单时间范围无效");
                }
                String topicName = nullableText(parameters.get("topicName"), 64);
                int size = Math.max(1, Math.min(optionalInt(parameters.get("size"), 5), 5));
                Map<String, Object> leaderboard =
                        leaderboardService.getGithubRepos(topicName, period, 1, size);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> records =
                        (List<Map<String, Object>>) leaderboard.getOrDefault("records", List.of());
                List<Map<String, Object>> selectedRecords =
                        List.copyOf(records.subList(0, Math.min(records.size(), size)));
                yield createCompleted(accountId, credentialId, action, parameters,
                        "查询 GitHub 项目榜单", balance, Map.of("records", selectedRecords),
                        selectedRecords.isEmpty()
                                ? "当前没有符合条件的 GitHub 榜单项目。"
                                : "已经找到 " + selectedRecords.size()
                                + " 个 GitHub 榜单项目，请选择编号查看卡片内容。");
            }
            case "launch_check.project.list" -> {
                List<Map<String, Object>> projects = launchCheckService.history(accountId);
                yield createCompleted(accountId, credentialId, action, parameters,
                        "查询最近的上线体检", balance, Map.of("projects", projects),
                        summarizeProjects(projects));
            }
            case "launch_check.project.create" -> prepareProjectCreate(
                    accountId, credentialId, action, parameters, balance);
            case "launch_check.stage.generate" -> prepareStageGenerate(
                    accountId, credentialId, action, parameters, balance);
            case "open_source.issue.list" -> prepareIssueList(
                    accountId, credentialId, action, parameters, balance);
            case "open_source.issue.detail" -> prepareIssueDetail(
                    accountId, credentialId, action, parameters, balance);
            case "open_source.issue.job_match" -> prepareIssuePaidAction(
                    accountId, credentialId, action, parameters, balance,
                    OpenSourceContributionService.JOB_MATCH_PRICING_KEY, "生成岗位匹配");
            case "open_source.issue.guide" -> prepareIssuePaidAction(
                    accountId, credentialId, action, parameters, balance,
                    OpenSourceContributionService.GUIDE_PRICING_KEY, "生成贡献指南");
            case "trend.analysis.generate" -> prepareTrendAnalysis(
                    accountId, credentialId, action, parameters, balance);
            case "trend.profile.generate" -> prepareTrendProfile(
                    accountId, credentialId, action, parameters, balance);
            case "plan.phase.generate" -> preparePlanPhase(
                    accountId, credentialId, action, parameters, balance);
            default -> throw new BaseException("不支持的设备动作");
        };
    }

    public Map<String, Object> confirm(Long accountId, Long credentialId, String taskId) {
        Map<String, Object> task = requireOwned(accountId, credentialId, taskId);
        String status = String.valueOf(task.get("status"));
        if (List.of("QUEUED", "RUNNING", "SUCCEEDED", "FAILED").contains(status)) return response(task);
        if (!"PREPARED".equals(status)) throw new BaseException("当前任务不能确认");
        LocalDateTime expiresAt = toLocalDateTime(task.get("expiresAt"));
        if (expiresAt == null || !expiresAt.isAfter(LocalDateTime.now())) {
            mapper.expire(taskId);
            return response(requireOwned(accountId, credentialId, taskId));
        }
        if (mapper.markQueued(taskId, "任务已确认，正在等待执行。") == 1) {
            executor.execute(() -> execute(taskId, accountId, credentialId));
        }
        return response(requireOwned(accountId, credentialId, taskId));
    }

    public Map<String, Object> status(Long accountId, Long credentialId, String taskId) {
        Map<String, Object> task = StringUtils.hasText(taskId)
                ? mapper.selectOwned(taskId, accountId, credentialId)
                : mapper.selectLatest(accountId, credentialId);
        if (task == null) throw new BaseException("暂无设备任务");
        if ("PREPARED".equals(task.get("status"))) {
            LocalDateTime expiresAt = toLocalDateTime(task.get("expiresAt"));
            if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
                mapper.expire(String.valueOf(task.get("id")));
                task = requireOwned(accountId, credentialId, String.valueOf(task.get("id")));
            }
        }
        return response(task);
    }

    public Map<String, Object> cancel(Long accountId, Long credentialId, String taskId) {
        if (mapper.cancel(taskId, accountId, credentialId) != 1) {
            throw new BaseException("任务已经开始、结束或不存在，当前不能取消");
        }
        return response(requireOwned(accountId, credentialId, taskId));
    }

    public List<Map<String, Object>> history(Long accountId, int limit) {
        List<Map<String, Object>> tasks = mapper.selectByAccount(
                accountId, Math.max(1, Math.min(limit, 100)));
        tasks.forEach(task -> task.put("result", parseNullable(task.remove("resultJson"))));
        return tasks;
    }

    public void deleteHistory(Long accountId, String taskId) {
        if (!StringUtils.hasText(taskId)
                || mapper.deleteTerminalByAccount(taskId, accountId) != 1) {
            throw new BaseException("任务不存在、尚未结束或无权删除");
        }
    }

    @PreDestroy
    public void shutdown() { executor.shutdownNow(); }

    private Map<String, Object> prepareProjectCreate(Long accountId, Long credentialId,
                                                      String action, Map<String, Object> parameters,
                                                      long balance) {
        String projectName = text(parameters.get("projectName"), 128);
        String summary = text(parameters.get("projectSummary"), 10_000);
        if (!StringUtils.hasText(projectName)) throw new BaseException("请说明项目名称");
        if (summary.length() < 20) throw new BaseException("项目说明至少需要 20 个字");
        String actionSummary = "创建上线体检项目：" + projectName;
        return createPrepared(accountId, credentialId, action, parameters, actionSummary, 0,
                balance, "准备" + actionSummary + "。该操作不扣配额，请确认是否继续。");
    }

    private Map<String, Object> prepareStageGenerate(Long accountId, Long credentialId,
                                                      String action, Map<String, Object> parameters,
                                                      long balance) {
        long projectId = number(parameters.get("projectId"), "请说明体检项目编号").longValue();
        int stageNo = number(parameters.get("stageNo"), "请说明体检阶段").intValue();
        if (stageNo < 1 || stageNo > LaunchCheckService.STAGE_COUNT) {
            throw new BaseException("体检阶段必须在 1 到 8 之间");
        }
        Map<String, Object> project = launchCheckService.detail(accountId, projectId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) project.get("stages");
        if (stageNo > 1 && stages.stream().noneMatch(
                stage -> ((Number) stage.get("stageNo")).intValue() == stageNo - 1)) {
            throw new BaseException("请先完成上一阶段");
        }
        int cost = pricingService.getCost(LaunchCheckService.PRICING_KEY);
        String summary = "为“" + project.get("projectName") + "”生成第 " + stageNo + " 阶段上线体检";
        return createPrepared(accountId, credentialId, action, parameters, summary, cost, balance,
                "准备" + summary + "。本次预计消耗 " + cost + " 点配额，请确认是否继续。");
    }

    private Map<String, Object> prepareIssueList(Long accountId, Long credentialId,
                                                  String action, Map<String, Object> parameters,
                                                  long balance) {
        Map<String, Object> result = openSourceService.list(1, 8,
                nullableText(parameters.get("difficulty"), 16),
                nullableText(parameters.get("participationStatus"), 32),
                nullableText(parameters.get("tech"), 64),
                nullableText(parameters.get("keyword"), 100),
                optionalInt(parameters.get("updatedDays"), 30));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.getOrDefault("records", List.of());
        return createCompleted(accountId, credentialId, action, parameters,
                "查询可参与的开源 Issue", balance, result, summarizeIssues(records));
    }

    private Map<String, Object> prepareIssueDetail(Long accountId, Long credentialId,
                                                    String action, Map<String, Object> parameters,
                                                    long balance) {
        long issueId = number(parameters.get("issueId"), "请说明 Issue 编号").longValue();
        Map<String, Object> issue = openSourceService.detail(issueId);
        return createCompleted(accountId, credentialId, action, parameters,
                "查看 Issue #" + issueId, balance, Map.of("issue", issue),
                "Issue " + issueId + "：“" + issue.get("titleZh") + "”。可参与状态为"
                        + issue.get("participationStatus") + "，难度为" + issue.get("difficulty") + "。");
    }

    private Map<String, Object> prepareIssuePaidAction(Long accountId, Long credentialId,
                                                       String action, Map<String, Object> parameters,
                                                       long balance, String pricingKey,
                                                       String verb) {
        long issueId = number(parameters.get("issueId"), "请说明 Issue 编号").longValue();
        Map<String, Object> issue = openSourceService.detail(issueId);
        int cost = pricingService.getCost(pricingKey);
        String summary = "为 Issue " + issueId + "“" + issue.get("titleZh") + "”" + verb;
        return createPrepared(accountId, credentialId, action, parameters, summary, cost, balance,
                "准备" + summary + "。本次预计消耗 " + cost + " 点配额，请确认是否继续。");
    }

    private Map<String, Object> prepareTrendAnalysis(Long accountId, Long credentialId,
                                                     String action, Map<String, Object> parameters,
                                                     long balance) {
        String question = text(parameters.get("question"), 2_000);
        if (question.length() < 5) throw new BaseException("请说明需要分析的趋势问题");
        int cost = pricingService.getCost("ai:trend:v1");
        String summary = "趋势分析：“" + abbreviate(question, 80) + "”";
        return createPrepared(accountId, credentialId, action, parameters, summary, cost, balance,
                "准备" + summary + "。本次预计消耗 " + cost + " 点配额，请确认是否继续。");
    }

    private Map<String, Object> prepareTrendProfile(Long accountId, Long credentialId,
                                                     String action, Map<String, Object> parameters,
                                                     long balance) {
        if (!(parameters.get("topics") instanceof List<?> topics) || topics.isEmpty()) {
            throw new BaseException("请至少说明一个感兴趣的技术方向");
        }
        for (String key : List.of("purpose", "experience", "timeCommitment")) {
            if (!StringUtils.hasText(text(parameters.get(key), 200))) {
                throw new BaseException("趋势分析画像信息不完整");
            }
        }
        int cost = trendProfileAnalysisService.cost();
        String summary = "根据语音画像生成智能趋势分析";
        return createPrepared(accountId, credentialId, action, parameters, summary, cost, balance,
                "画像信息已经填写完成。开始智能分析将消耗 " + cost + " 点配额，请确认是否继续。");
    }

    private Map<String, Object> preparePlanPhase(Long accountId, Long credentialId,
                                                  String action, Map<String, Object> parameters,
                                                  long balance) {
        int phase = number(parameters.get("phase"), "请说明方案阶段").intValue();
        if (phase < 1 || phase > 4) throw new BaseException("方案阶段必须在 1 到 4 之间");
        if (phase > 1) inheritPreviousPlanPhase(accountId, credentialId, phase, parameters);
        String summaryText = text(parameters.get("summary"), 10_000);
        if (summaryText.length() < 10) throw new BaseException("项目描述至少需要 10 个字");
        if (phase > 1 && !StringUtils.hasText(text(parameters.get("prevContents"), 30_000))) {
            throw new BaseException("生成第 2 到 4 阶段时需要提供前序阶段内容");
        }
        int cost = planGenerationService.cost();
        String summary = "生成方案设计第 " + phase + " 阶段";
        return createPrepared(accountId, credentialId, action, parameters, summary, cost, balance,
                "准备" + summary + "。本次预计消耗 " + cost + " 点配额，请确认是否继续。");
    }

    private void inheritPreviousPlanPhase(Long accountId, Long credentialId, int phase,
                                          Map<String, Object> parameters) {
        boolean hasSummary = StringUtils.hasText(text(parameters.get("summary"), 10_000));
        boolean hasPrevious = StringUtils.hasText(text(parameters.get("prevContents"), 30_000));
        if (hasSummary && hasPrevious) return;

        Map<String, Object> previous = mapper.selectLatestSucceeded(
                accountId, credentialId, "plan.phase.generate");
        if (previous == null) throw new BaseException("请先生成方案设计第 " + (phase - 1) + " 阶段");
        Map<String, Object> previousResult = parseMap(previous.get("resultJson"));
        Number previousPhase = number(previousResult.get("phase"), "上一阶段方案记录无效");
        if (previousPhase.intValue() != phase - 1) {
            throw new BaseException("请先生成方案设计第 " + (phase - 1) + " 阶段");
        }
        Map<String, Object> previousParameters = parseMap(previous.get("parametersJson"));
        if (!hasSummary) parameters.put("summary", previousParameters.get("summary"));
        if (!hasPrevious) parameters.put("prevContents", previousResult.get("content"));
        if (!parameters.containsKey("mode") && previousParameters.containsKey("mode")) {
            parameters.put("mode", previousParameters.get("mode"));
        }
    }

    private Map<String, Object> createPrepared(Long accountId, Long credentialId, String action,
                                                Map<String, Object> parameters, String summary,
                                                int cost, long balance, String spoken) {
        Map<String, Object> task = baseTask(accountId, credentialId, action, parameters,
                summary, "PREPARED", cost, balance, spoken);
        task.put("expiresAt", LocalDateTime.now().plusMinutes(5));
        mapper.insert(task);
        return response(requireOwned(accountId, credentialId, String.valueOf(task.get("id"))));
    }

    private Map<String, Object> createCompleted(Long accountId, Long credentialId, String action,
                                                 Map<String, Object> parameters, String summary,
                                                 long balance, Object result, String spoken) {
        Map<String, Object> task = baseTask(accountId, credentialId, action, parameters,
                summary, "SUCCEEDED", 0, balance, spoken);
        task.put("resultJson", json(result));
        mapper.insert(task);
        return response(requireOwned(accountId, credentialId, String.valueOf(task.get("id"))));
    }

    private Map<String, Object> baseTask(Long accountId, Long credentialId, String action,
                                         Map<String, Object> parameters, String summary,
                                         String status, int cost, long balance, String spoken) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", UUID.randomUUID().toString());
        task.put("accountId", accountId);
        task.put("credentialId", credentialId);
        task.put("actionKey", action);
        task.put("parametersJson", json(parameters));
        task.put("actionSummary", summary);
        task.put("status", status);
        task.put("quotaCost", cost);
        task.put("balance", balance);
        task.put("resultJson", null);
        task.put("spokenSummary", spoken);
        task.put("expiresAt", null);
        return task;
    }

    private void execute(String taskId, Long accountId, Long credentialId) {
        if (mapper.markRunning(taskId, "任务正在 OrangeVC 服务器执行。") != 1) return;
        BaseContext.setCurrentId(accountId);
        try {
            Map<String, Object> task = requireOwned(accountId, credentialId, taskId);
            Map<String, Object> params = parseMap(task.get("parametersJson"));
            Map<String, Object> result;
            String spoken;
            if ("launch_check.project.create".equals(task.get("actionKey"))) {
                result = launchCheckService.create(accountId, params);
                spoken = "上线体检项目“" + result.get("projectName") + "”已经创建。";
            } else if ("launch_check.stage.generate".equals(task.get("actionKey"))) {
                long projectId = number(params.get("projectId"), "项目编号无效").longValue();
                int stageNo = number(params.get("stageNo"), "阶段编号无效").intValue();
                result = launchCheckService.generate(accountId, projectId, stageNo);
                spoken = "第 " + stageNo + " 阶段上线体检已经生成，可在 OrangeVC 报告记录中查看。";
            } else if ("open_source.issue.job_match".equals(task.get("actionKey"))) {
                long issueId = number(params.get("issueId"), "Issue 编号无效").longValue();
                result = openSourceService.jobMatch(accountId, issueId);
                spoken = "Issue " + issueId + " 的岗位匹配已经生成，可在 OrangeVC 中查看完整结果。";
            } else if ("open_source.issue.guide".equals(task.get("actionKey"))) {
                long issueId = number(params.get("issueId"), "Issue 编号无效").longValue();
                result = openSourceService.generateGuide(accountId, issueId);
                spoken = "Issue " + issueId + " 的贡献指南已经生成，可在 OrangeVC 中查看完整内容。";
            } else if ("trend.analysis.generate".equals(task.get("actionKey"))) {
                String question = text(params.get("question"), 2_000);
                result = trendAnalysisService.analyze(question);
                if (result.containsKey("error")) throw new BaseException(String.valueOf(result.get("error")));
                spoken = "趋势分析已经完成，可在智能设备的最近语音任务中查看完整结果。";
            } else if ("trend.profile.generate".equals(task.get("actionKey"))) {
                result = trendProfileAnalysisService.analyze(accountId, params);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> recommendations =
                        (List<Map<String, Object>>) result.getOrDefault("recommendations", List.of());
                spoken = recommendations.isEmpty()
                        ? "智能趋势分析已经完成，但本次没有生成项目推荐。"
                        : "智能趋势分析已经完成，共生成 " + recommendations.size() + " 个项目推荐。";
            } else if ("plan.phase.generate".equals(task.get("actionKey"))) {
                result = planGenerationService.generate(accountId, params);
                spoken = "方案设计第 " + params.get("phase") + " 阶段已经完成，可在最近语音任务中查看。";
            } else throw new BaseException("不支持的异步动作");
            mapper.markSucceeded(taskId, json(result), quotaService.getBalance(accountId), spoken);
        } catch (Exception e) {
            mapper.markFailed(taskId, "ACTION_FAILED",
                    "任务执行失败。本次未产生最终结果；如已扣配额，OrangeVC 会按原业务规则自动退回。");
        } finally {
            BaseContext.clear();
        }
    }

    private Map<String, Object> response(Map<String, Object> raw) {
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
        result.put("result", parseNullable(raw.get("resultJson")));
        return result;
    }

    private Map<String, Object> requireOwned(Long accountId, Long credentialId, String taskId) {
        Map<String, Object> task = mapper.selectOwned(taskId, accountId, credentialId);
        if (task == null) throw new BaseException("设备任务不存在");
        return task;
    }

    private Map<String, Object> parameters(Object value) {
        if (value == null) return new LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> map)) throw new BaseException("动作参数格式不正确");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private String summarizeProjects(List<Map<String, Object>> projects) {
        if (projects.isEmpty()) return "你还没有上线体检项目。";
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(3, projects.size()); i++)
            names.add("编号 " + projects.get(i).get("id") + " “"
                    + projects.get(i).get("projectName") + "”");
        return "你最近的上线体检项目有：" + String.join("、", names) + "。";
    }

    private String summarizeIssues(List<Map<String, Object>> issues) {
        if (issues.isEmpty()) return "当前没有符合条件的可参与 Issue。";
        List<String> items = new ArrayList<>();
        for (int i = 0; i < Math.min(5, issues.size()); i++) {
            Map<String, Object> issue = issues.get(i);
            items.add("编号 " + issue.get("id") + " “" + issue.get("titleZh") + "”");
        }
        return "找到这些开源 Issue：" + String.join("、", items) + "。你可以继续查询某个编号的详情。";
    }

    private String summarizeTasks(List<Map<String, Object>> tasks) {
        if (tasks.isEmpty()) return "你还没有 OrangeVC 语音任务记录。";
        List<String> items = new ArrayList<>();
        for (int i = 0; i < Math.min(5, tasks.size()); i++) {
            Map<String, Object> task = tasks.get(i);
            items.add(String.valueOf(task.getOrDefault("actionSummary", "未命名任务"))
                    + "，状态" + task.getOrDefault("status", "未知"));
        }
        return "你最近的语音任务有：" + String.join("；", items) + "。";
    }

    private String nullableText(Object value, int maxLength) {
        String result = text(value, maxLength);
        return StringUtils.hasText(result) ? result : null;
    }

    private Integer optionalInt(Object value, int fallback) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) return fallback;
        return number(value, "更新时间范围无效").intValue();
    }

    private String abbreviate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private boolean hasScope(String scopes, String action) {
        return StringUtils.hasText(scopes) && List.of(scopes.split(",")).contains(action);
    }

    private String text(Object value, int maxLength) {
        String result = value == null ? "" : String.valueOf(value).trim();
        if (result.length() > maxLength) throw new BaseException("输入内容过长");
        return result;
    }

    private Number number(Object value, String message) {
        if (value instanceof Number number) return number;
        try { return Long.parseLong(String.valueOf(value)); }
        catch (Exception e) { throw new BaseException(message); }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("动作数据序列化失败", e); }
    }

    private Map<String, Object> parseMap(Object value) {
        Object parsed = parseNullable(value);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private Object parseNullable(Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) return value;
        try {
            if (value instanceof byte[] bytes) {
                return objectMapper.readValue(bytes, new TypeReference<Object>() {});
            }
            return objectMapper.readValue(String.valueOf(value), new TypeReference<Object>() {});
        }
        catch (Exception e) { return null; }
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime time) return time;
        if (value == null) return null;
        try { return LocalDateTime.parse(String.valueOf(value)); }
        catch (Exception e) { return null; }
    }
}
