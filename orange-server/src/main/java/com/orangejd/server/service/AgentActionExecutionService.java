package com.orangejd.server.service;
import com.orangejd.common.context.BaseContext; import com.orangejd.common.exception.BaseException; import com.orangejd.server.mapper.AgentActionMapper;
import java.time.LocalDateTime; import java.util.*; import java.util.concurrent.*;
final class AgentActionExecutionService {
 private final AgentActionMapper mapper; private final LaunchCheckService launchCheckService; private final QuotaService quotaService; private final OpenSourceContributionService openSourceService; private final TrendAnalysisService trendAnalysisService; private final TrendProfileAnalysisService trendProfileAnalysisService; private final PlanGenerationService planGenerationService; private final AgentActionRepository repository; private final AgentActionSupport support; private final ExecutorService executor;
 AgentActionExecutionService(AgentActionMapper mapper,LaunchCheckService launch,QuotaService quota,OpenSourceContributionService openSource,TrendAnalysisService trend,TrendProfileAnalysisService trendProfile,PlanGenerationService plans,AgentActionRepository repository,AgentActionSupport support){this.mapper=mapper;this.launchCheckService=launch;this.quotaService=quota;this.openSourceService=openSource;this.trendAnalysisService=trend;this.trendProfileAnalysisService=trendProfile;this.planGenerationService=plans;this.repository=repository;this.support=support;this.executor=Executors.newFixedThreadPool(2,r->{var thread=new Thread(r,"agent-action-worker");thread.setDaemon(true);return thread;});}
Map<String, Object> confirm(Long accountId, Long credentialId, String taskId) {
        Map<String, Object> task = repository.requireOwned(accountId, credentialId, taskId);
        String status = String.valueOf(task.get("status"));
        if (List.of("QUEUED", "RUNNING", "SUCCEEDED", "FAILED").contains(status)) return repository.response(task);
        if (!"PREPARED".equals(status)) throw new BaseException("当前任务不能确认");
        LocalDateTime expiresAt = support.toLocalDateTime(task.get("expiresAt"));
        if (expiresAt == null || !expiresAt.isAfter(LocalDateTime.now())) {
            mapper.expire(taskId);
            return repository.response(repository.requireOwned(accountId, credentialId, taskId));
        }
        if (mapper.markQueued(taskId, "任务已确认，正在等待执行。") == 1) {
            executor.execute(() -> execute(taskId, accountId, credentialId));
        }
        return repository.response(repository.requireOwned(accountId, credentialId, taskId));
    }

void execute(String taskId, Long accountId, Long credentialId) {
        if (mapper.markRunning(taskId, "任务正在 OrangeVC 服务器执行。") != 1) return;
        BaseContext.setCurrentId(accountId);
        try {
            Map<String, Object> task = repository.requireOwned(accountId, credentialId, taskId);
            Map<String, Object> params = support.parseMap(task.get("parametersJson"));
            Map<String, Object> result;
            String spoken;
            if ("launch_check.project.create".equals(task.get("actionKey"))) {
                result = launchCheckService.create(accountId, params);
                spoken = "上线体检项目“" + result.get("projectName") + "”已经创建。";
            } else if ("launch_check.stage.generate".equals(task.get("actionKey"))) {
                long projectId = support.number(params.get("projectId"), "项目编号无效").longValue();
                int stageNo = support.number(params.get("stageNo"), "阶段编号无效").intValue();
                result = launchCheckService.generate(accountId, projectId, stageNo);
                spoken = "第 " + stageNo + " 阶段上线体检已经生成，可在 OrangeVC 报告记录中查看。";
            } else if ("open_source.issue.job_match".equals(task.get("actionKey"))) {
                long issueId = support.number(params.get("issueId"), "Issue 编号无效").longValue();
                result = openSourceService.jobMatch(accountId, issueId);
                spoken = "Issue " + issueId + " 的岗位匹配已经生成，可在 OrangeVC 中查看完整结果。";
            } else if ("open_source.issue.guide".equals(task.get("actionKey"))) {
                long issueId = support.number(params.get("issueId"), "Issue 编号无效").longValue();
                result = openSourceService.generateGuide(accountId, issueId);
                spoken = "Issue " + issueId + " 的贡献指南已经生成，可在 OrangeVC 中查看完整内容。";
            } else if ("trend.analysis.generate".equals(task.get("actionKey"))) {
                String question = support.text(params.get("question"), 2_000);
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
            mapper.markSucceeded(taskId, support.json(result), quotaService.getBalance(accountId), spoken);
        } catch (Exception e) {
            mapper.markFailed(taskId, "ACTION_FAILED",
                    "任务执行失败。本次未产生最终结果；如已扣配额，OrangeVC 会按原业务规则自动退回。");
        } finally {
            BaseContext.clear();
        }
    }
 void shutdown(){executor.shutdownNow();}
}

