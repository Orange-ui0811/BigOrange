package com.orangejd.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.mapper.AgentActionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.orangejd.common.exception.BaseException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentActionServiceTest {
    private AgentActionMapper mapper;
    private LaunchCheckService launchCheckService;
    private QuotaService quotaService;
    private PricingService pricingService;
    private OpenSourceContributionService openSourceService;
    private TrendAnalysisService trendAnalysisService;
    private TrendProfileAnalysisService trendProfileAnalysisService;
    private PlanGenerationService planGenerationService;
    private NotificationService notificationService;
    private LeaderboardService leaderboardService;
    private AgentActionService service;
    private final AtomicReference<Map<String, Object>> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        mapper = mock(AgentActionMapper.class);
        launchCheckService = mock(LaunchCheckService.class);
        quotaService = mock(QuotaService.class);
        pricingService = mock(PricingService.class);
        openSourceService = mock(OpenSourceContributionService.class);
        trendAnalysisService = mock(TrendAnalysisService.class);
        trendProfileAnalysisService = mock(TrendProfileAnalysisService.class);
        planGenerationService = mock(PlanGenerationService.class);
        notificationService = mock(NotificationService.class);
        leaderboardService = mock(LeaderboardService.class);
        when(quotaService.getBalance(7L)).thenReturn(88L);
        doAnswer(invocation -> {
            stored.set(new LinkedHashMap<>(invocation.getArgument(0)));
            return 1;
        }).when(mapper).insert(any());
        when(mapper.selectOwned(any(), anyLong(), anyLong()))
                .thenAnswer(invocation -> stored.get() == null ? null : new LinkedHashMap<>(stored.get()));
        service = new AgentActionService(mapper, launchCheckService, quotaService,
                pricingService, openSourceService, trendAnalysisService,
                trendProfileAnalysisService, planGenerationService,
                notificationService, leaderboardService, new ObjectMapper());
    }

    @AfterEach
    void tearDown() { service.shutdown(); }

    @Test
    void balanceReadCompletesWithoutConfirmation() {
        Map<String, Object> result = service.prepare(7L, 9L, AgentDeviceAuthService.SCOPES,
                Map.of("action", "quota.balance.read", "parameters", Map.of()));

        assertEquals("SUCCEEDED", result.get("status"));
        assertFalse((Boolean) result.get("needsConfirmation"));
        assertEquals(88L, result.get("balance"));
    }

    @Test
    void unreadNotificationReadCompletesWithoutConfirmation() {
        when(notificationService.unreadCount()).thenReturn(3);

        Map<String, Object> result = service.prepare(7L, 9L, AgentDeviceAuthService.SCOPES,
                Map.of("action", "notification.unread.read", "parameters", Map.of()));

        assertEquals("SUCCEEDED", result.get("status"));
        assertFalse((Boolean) result.get("needsConfirmation"));
        assertTrue(String.valueOf(result.get("spokenMessage")).contains("3 条"));
    }

    @Test
    void recentTaskReadIsLimitedAndCompletesWithoutConfirmation() {
        when(mapper.selectByAccount(7L, 5)).thenReturn(List.of(
                new LinkedHashMap<>(Map.of(
                        "actionSummary", "生成智能分析",
                        "status", "SUCCEEDED",
                        "resultJson", "{}"))));

        Map<String, Object> result = service.prepare(7L, 9L, AgentDeviceAuthService.SCOPES,
                Map.of("action", "agent.task.list", "parameters", Map.of("limit", 5)));

        assertEquals("SUCCEEDED", result.get("status"));
        assertFalse((Boolean) result.get("needsConfirmation"));
        assertTrue(String.valueOf(result.get("spokenMessage")).contains("生成智能分析"));
    }

    @Test
    void githubLeaderboardReadReturnsAtMostFiveCardsWithoutConfirmation() {
        when(leaderboardService.getGithubRepos("ai-agents", "week", 1, 5))
                .thenReturn(Map.of("records", List.of(
                        Map.of("name", "one", "stars", 100),
                        Map.of("name", "two", "stars", 90))));

        Map<String, Object> result = service.prepare(7L, 9L, AgentDeviceAuthService.SCOPES,
                Map.of("action", "leaderboard.github.list",
                        "parameters", Map.of(
                                "period", "week",
                                "topicName", "ai-agents",
                                "size", 5)));

        assertEquals("SUCCEEDED", result.get("status"));
        assertFalse((Boolean) result.get("needsConfirmation"));
        assertTrue(String.valueOf(result.get("spokenMessage")).contains("2 个"));
    }

    @Test
    void paidStageOnlyPreparesAndDoesNotGenerate() {
        Map<String, Object> project = new LinkedHashMap<>();
        project.put("id", 12L);
        project.put("projectName", "OrangeJD");
        project.put("stages", List.of());
        when(launchCheckService.detail(7L, 12L)).thenReturn(project);
        when(pricingService.getCost(LaunchCheckService.PRICING_KEY)).thenReturn(40);

        Map<String, Object> result = service.prepare(7L, 9L, AgentDeviceAuthService.SCOPES,
                Map.of("action", "launch_check.stage.generate",
                        "parameters", Map.of("projectId", 12, "stageNo", 1)));

        assertEquals("PREPARED", result.get("status"));
        assertTrue((Boolean) result.get("needsConfirmation"));
        assertEquals(40, result.get("quotaCost"));
        verify(launchCheckService, never()).generate(anyLong(), anyLong(), any(Integer.class));
    }

    @Test
    void issueGuideUsesExistingPriceAndRequiresConfirmation() {
        when(openSourceService.detail(27L)).thenReturn(Map.of(
                "id", 27L, "titleZh", "修复前端无障碍标签"));
        when(pricingService.getCost(OpenSourceContributionService.GUIDE_PRICING_KEY)).thenReturn(35);

        Map<String, Object> result = service.prepare(7L, 9L, AgentDeviceAuthService.SCOPES,
                Map.of("action", "open_source.issue.guide",
                        "parameters", Map.of("issueId", 27)));

        assertEquals("PREPARED", result.get("status"));
        assertTrue((Boolean) result.get("needsConfirmation"));
        assertEquals(35, result.get("quotaCost"));
        verify(openSourceService, never()).generateGuide(anyLong(), anyLong());
    }

    @Test
    void trendAnalysisUsesExistingPriceAndRequiresConfirmation() {
        when(pricingService.getCost("ai:trend:v1")).thenReturn(45);

        Map<String, Object> result = service.prepare(7L, 9L, AgentDeviceAuthService.SCOPES,
                Map.of("action", "trend.analysis.generate",
                        "parameters", Map.of("question", "分析 React 服务端组件的近期趋势")));

        assertEquals("PREPARED", result.get("status"));
        assertEquals(45, result.get("quotaCost"));
        verify(trendAnalysisService, never()).analyze(any());
    }

    @Test
    void guidedTrendProfileUsesV2PriceAndRequiresConfirmation() {
        when(trendProfileAnalysisService.cost()).thenReturn(50);

        Map<String, Object> result = service.prepare(7L, 9L, AgentDeviceAuthService.SCOPES,
                Map.of("action", "trend.profile.generate", "parameters", Map.of(
                        "topics", List.of("AI智能体"),
                        "purpose", "学习 AI 编程知识",
                        "experience", "新手入门",
                        "languages", List.of("Python"),
                        "timeCommitment", "每周5-15小时")));

        assertEquals("PREPARED", result.get("status"));
        assertEquals(50, result.get("quotaCost"));
        verify(trendProfileAnalysisService, never()).analyze(anyLong(), any());
    }

    @Test
    void nextPlanPhaseInheritsPreviousTaskResult() throws Exception {
        when(planGenerationService.cost()).thenReturn(40);
        when(mapper.selectLatestSucceeded(7L, 9L, "plan.phase.generate"))
                .thenReturn(Map.of(
                        "parametersJson", "{\"phase\":1,\"summary\":\"设计一个面向开发者的协作平台\"}",
                        "resultJson", "{\"phase\":1,\"content\":\"第一阶段方案内容\"}"));

        Map<String, Object> result = service.prepare(7L, 9L, AgentDeviceAuthService.SCOPES,
                Map.of("action", "plan.phase.generate",
                        "parameters", Map.of("phase", 2)));

        assertEquals("PREPARED", result.get("status"));
        Map<String, Object> parameters = new ObjectMapper().readValue(
                String.valueOf(stored.get().get("parametersJson")), Map.class);
        assertEquals("第一阶段方案内容", parameters.get("prevContents"));
        assertEquals("设计一个面向开发者的协作平台", parameters.get("summary"));
    }

    @Test
    void deletesOnlyOwnedTerminalTask() {
        when(mapper.deleteTerminalByAccount("task-1", 7L)).thenReturn(1);

        service.deleteHistory(7L, "task-1");

        verify(mapper).deleteTerminalByAccount("task-1", 7L);
    }

    @Test
    void rejectsDeletingRunningOrForeignTask() {
        when(mapper.deleteTerminalByAccount("task-1", 7L)).thenReturn(0);

        assertThrows(BaseException.class, () -> service.deleteHistory(7L, "task-1"));
    }
}
