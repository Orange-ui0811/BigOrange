package com.orangejd.server.service;

import com.orangejd.server.dto.BeginnerProjectView;
import com.orangejd.server.dto.CareerProjectView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrendProjectMatchExplainerTest {
    private final TrendProjectMatchExplainer explainer = new TrendProjectMatchExplainer();
    private final TrendAnalysisInput input = new TrendAnalysisInput(
            List.of("AI智能体"), "应对求职面试", "新手入门", List.of("TypeScript"),
            "每周5-15小时", "", "", "", "", "", true, true, true, true);

    @Test
    void explainsBeginnerMatchFromTheCurrentProfile() {
        BeginnerProjectView project = mock(BeginnerProjectView.class);
        when(project.fullName()).thenReturn("sample/agent-starter");
        when(project.descriptionZh()).thenReturn("AI智能体入门模板");
        when(project.techStack()).thenReturn("TypeScript React");
        when(project.primaryLanguage()).thenReturn("TypeScript");
        when(project.difficulty()).thenReturn("入门");
        when(project.estimatedDuration()).thenReturn("2-5天");

        String result = explainer.beginner(input, project);

        assertTrue(result.contains("AI智能体"));
        assertTrue(result.contains("TypeScript"));
        assertTrue(result.contains("2-5天"));
    }

    @Test
    void explainsCareerMatchFromRoleAndJobSeekingPurpose() {
        CareerProjectView project = mock(CareerProjectView.class);
        when(project.fullName()).thenReturn("sample/agent-platform");
        when(project.summaryZh()).thenReturn("AI智能体工程平台");
        when(project.techStack()).thenReturn("TypeScript Node.js");
        when(project.primaryLanguage()).thenReturn("TypeScript");
        when(project.bestRoleName()).thenReturn("AI应用与Agent");
        when(project.estimatedDuration()).thenReturn("2-4周");

        String result = explainer.career(input, project);

        assertTrue(result.contains("AI应用与Agent"));
        assertTrue(result.contains("作品证明和面试讨论点"));
        assertTrue(result.contains("2-4周"));
    }
}
