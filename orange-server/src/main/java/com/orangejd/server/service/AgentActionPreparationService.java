package com.orangejd.server.service;
import com.orangejd.common.exception.BaseException; import org.springframework.util.StringUtils; import java.util.*;
final class AgentActionPreparationService {
 private final QuotaService quotaService; private final PricingService pricingService; private final LaunchCheckService launchCheckService; private final OpenSourceContributionService openSourceService; private final TrendProfileAnalysisService trendProfileAnalysisService; private final PlanGenerationService planGenerationService; private final NotificationService notificationService; private final LeaderboardService leaderboardService; private final AgentActionRepository repository; private final AgentActionSupport support;
 AgentActionPreparationService(QuotaService quota, PricingService pricing, LaunchCheckService launch, OpenSourceContributionService openSource, TrendProfileAnalysisService trendProfile, PlanGenerationService plans, NotificationService notifications, LeaderboardService leaderboards, AgentActionRepository repository, AgentActionSupport support){this.quotaService=quota;this.pricingService=pricing;this.launchCheckService=launch;this.openSourceService=openSource;this.trendProfileAnalysisService=trendProfile;this.planGenerationService=plans;this.notificationService=notifications;this.leaderboardService=leaderboards;this.repository=repository;this.support=support;}
Map<String, Object> prepare(Long accountId, Long credentialId,
                                       String scopes, Map<String, Object> body) {
        String action = support.text(body.get("action"), 64);
        if (!AgentActionSupport.ALLOWED_ACTIONS.contains(action) || !support.hasScope(scopes, action)) {
            throw new BaseException("设备没有执行该动作的权限");
        }
        Map<String, Object> parameters = support.parameters(body.get("parameters"));
        long balance = quotaService.getBalance(accountId);
        return switch (action) {
            case "quota.balance.read" -> repository.createCompleted(accountId, credentialId, action,
                    parameters, "查询 OrangeVC 配额", balance, Map.of("balance", balance),
                    "你当前有 " + balance + " 点 OrangeVC 配额。");
            case "agent.task.list" -> {
                int limit = Math.max(1, Math.min(support.optionalInt(parameters.get("limit"), 5), 10));
                List<Map<String, Object>> tasks = repository.history(accountId, limit);
                yield repository.createCompleted(accountId, credentialId, action, parameters,
                        "查询最近语音任务", balance, Map.of("tasks", tasks),
                        support.summarizeTasks(tasks));
            }
            case "notification.unread.read" -> {
                int unread = notificationService.unreadCount();
                yield repository.createCompleted(accountId, credentialId, action, parameters,
                        "查询未读通知", balance, Map.of("unreadCount", unread),
                        unread == 0
                                ? "你当前没有未读通知。"
                                : "你当前有 " + unread + " 条 OrangeVC 未读通知。");
            }
            case "leaderboard.github.list" -> {
                String period = support.text(parameters.getOrDefault("period", "week"), 16);
                if (!List.of("week", "new", "all").contains(period)) {
                    throw new BaseException("GitHub 榜单时间范围无效");
                }
                String topicName = support.nullableText(parameters.get("topicName"), 64);
                int size = Math.max(1, Math.min(support.optionalInt(parameters.get("size"), 5), 5));
                Map<String, Object> leaderboard =
                        leaderboardService.getGithubRepos(topicName, period, 1, size);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> records =
                        (List<Map<String, Object>>) leaderboard.getOrDefault("records", List.of());
                List<Map<String, Object>> selectedRecords =
                        List.copyOf(records.subList(0, Math.min(records.size(), size)));
                yield repository.createCompleted(accountId, credentialId, action, parameters,
                        "查询 GitHub 项目榜单", balance, Map.of("records", selectedRecords),
                        selectedRecords.isEmpty()
                                ? "当前没有符合条件的 GitHub 榜单项目。"
                                : "已经找到 " + selectedRecords.size()
                                + " 个 GitHub 榜单项目，请选择编号查看卡片内容。");
            }
            case "launch_check.project.list" -> {
                List<Map<String, Object>> projects = launchCheckService.history(accountId);
                yield repository.createCompleted(accountId, credentialId, action, parameters,
                        "查询最近的上线体检", balance, Map.of("projects", projects),
                        support.summarizeProjects(projects));
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

Map<String, Object> prepareProjectCreate(Long accountId, Long credentialId,
                                                      String action, Map<String, Object> parameters,
                                                      long balance) {
        String projectName = support.text(parameters.get("projectName"), 128);
        String summary = support.text(parameters.get("projectSummary"), 10_000);
        if (!StringUtils.hasText(projectName)) throw new BaseException("请说明项目名称");
        if (summary.length() < 20) throw new BaseException("项目说明至少需要 20 个字");
        String actionSummary = "创建上线体检项目：" + projectName;
        return repository.createPrepared(accountId, credentialId, action, parameters, actionSummary, 0,
                balance, "准备" + actionSummary + "。该操作不扣配额，请确认是否继续。");
    }

Map<String, Object> prepareStageGenerate(Long accountId, Long credentialId,
                                                      String action, Map<String, Object> parameters,
                                                      long balance) {
        long projectId = support.number(parameters.get("projectId"), "请说明体检项目编号").longValue();
        int stageNo = support.number(parameters.get("stageNo"), "请说明体检阶段").intValue();
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
        return repository.createPrepared(accountId, credentialId, action, parameters, summary, cost, balance,
                "准备" + summary + "。本次预计消耗 " + cost + " 点配额，请确认是否继续。");
    }

Map<String, Object> prepareIssueList(Long accountId, Long credentialId,
                                                  String action, Map<String, Object> parameters,
                                                  long balance) {
        Map<String, Object> result = openSourceService.list(1, 8,
                support.nullableText(parameters.get("difficulty"), 16),
                support.nullableText(parameters.get("participationStatus"), 32),
                support.nullableText(parameters.get("tech"), 64),
                support.nullableText(parameters.get("keyword"), 100),
                support.optionalInt(parameters.get("updatedDays"), 30));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.getOrDefault("records", List.of());
        return repository.createCompleted(accountId, credentialId, action, parameters,
                "查询可参与的开源 Issue", balance, result, support.summarizeIssues(records));
    }

Map<String, Object> prepareIssueDetail(Long accountId, Long credentialId,
                                                    String action, Map<String, Object> parameters,
                                                    long balance) {
        long issueId = support.number(parameters.get("issueId"), "请说明 Issue 编号").longValue();
        Map<String, Object> issue = openSourceService.detail(issueId);
        return repository.createCompleted(accountId, credentialId, action, parameters,
                "查看 Issue #" + issueId, balance, Map.of("issue", issue),
                "Issue " + issueId + "：“" + issue.get("titleZh") + "”。可参与状态为"
                        + issue.get("participationStatus") + "，难度为" + issue.get("difficulty") + "。");
    }

Map<String, Object> prepareIssuePaidAction(Long accountId, Long credentialId,
                                                       String action, Map<String, Object> parameters,
                                                       long balance, String pricingKey,
                                                       String verb) {
        long issueId = support.number(parameters.get("issueId"), "请说明 Issue 编号").longValue();
        Map<String, Object> issue = openSourceService.detail(issueId);
        int cost = pricingService.getCost(pricingKey);
        String summary = "为 Issue " + issueId + "“" + issue.get("titleZh") + "”" + verb;
        return repository.createPrepared(accountId, credentialId, action, parameters, summary, cost, balance,
                "准备" + summary + "。本次预计消耗 " + cost + " 点配额，请确认是否继续。");
    }

Map<String, Object> prepareTrendAnalysis(Long accountId, Long credentialId,
                                                     String action, Map<String, Object> parameters,
                                                     long balance) {
        String question = support.text(parameters.get("question"), 2_000);
        if (question.length() < 5) throw new BaseException("请说明需要分析的趋势问题");
        int cost = pricingService.getCost("ai:trend:v1");
        String summary = "趋势分析：“" + support.abbreviate(question, 80) + "”";
        return repository.createPrepared(accountId, credentialId, action, parameters, summary, cost, balance,
                "准备" + summary + "。本次预计消耗 " + cost + " 点配额，请确认是否继续。");
    }

Map<String, Object> prepareTrendProfile(Long accountId, Long credentialId,
                                                     String action, Map<String, Object> parameters,
                                                     long balance) {
        if (!(parameters.get("topics") instanceof List<?> topics) || topics.isEmpty()) {
            throw new BaseException("请至少说明一个感兴趣的技术方向");
        }
        for (String key : List.of("purpose", "experience", "timeCommitment")) {
            if (!StringUtils.hasText(support.text(parameters.get(key), 200))) {
                throw new BaseException("趋势分析画像信息不完整");
            }
        }
        int cost = trendProfileAnalysisService.cost();
        String summary = "根据语音画像生成智能趋势分析";
        return repository.createPrepared(accountId, credentialId, action, parameters, summary, cost, balance,
                "画像信息已经填写完成。开始智能分析将消耗 " + cost + " 点配额，请确认是否继续。");
    }

Map<String, Object> preparePlanPhase(Long accountId, Long credentialId,
                                                  String action, Map<String, Object> parameters,
                                                  long balance) {
        int phase = support.number(parameters.get("phase"), "请说明方案阶段").intValue();
        if (phase < 1 || phase > 4) throw new BaseException("方案阶段必须在 1 到 4 之间");
        if (phase > 1) repository.inheritPreviousPlanPhase(accountId, credentialId, phase, parameters);
        String summaryText = support.text(parameters.get("summary"), 10_000);
        if (summaryText.length() < 10) throw new BaseException("项目描述至少需要 10 个字");
        if (phase > 1 && !StringUtils.hasText(support.text(parameters.get("prevContents"), 30_000))) {
            throw new BaseException("生成第 2 到 4 阶段时需要提供前序阶段内容");
        }
        int cost = planGenerationService.cost();
        String summary = "生成方案设计第 " + phase + " 阶段";
        return repository.createPrepared(accountId, credentialId, action, parameters, summary, cost, balance,
                "准备" + summary + "。本次预计消耗 " + cost + " 点配额，请确认是否继续。");
    }
}
