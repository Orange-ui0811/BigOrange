package com.orangejd.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.pojo.entity.BeginnerProject;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;
import com.orangejd.server.foundation.external.ExternalAiClient;
import com.orangejd.server.foundation.external.TranslationCacheMapper;
import com.orangejd.server.mapper.BeginnerProjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class BeginnerProjectServiceTest {
    @Test
    void recommendationFallsBackFromLanguageWithoutInventingRepositories() {
        BeginnerProjectMapper mapper = mock(BeginnerProjectMapper.class);
        BeginnerProject project = new BeginnerProject();
        project.setId(7L);
        project.setFullName("owner/project");
        project.setRepositoryName("project");
        project.setOwnerName("owner");
        project.setHtmlUrl("https://github.com/owner/project");
        project.setTopicsJson("[]");
        project.setStatus("PUBLISHED");
        project.setBeginnerScore(88);
        when(mapper.selectRecommended(List.of("AI智能体"), List.of("Java"), 3)).thenReturn(List.of());
        when(mapper.selectRecommended(List.of("AI智能体"), List.of(), 3)).thenReturn(List.of(project));

        BeginnerProjectService service = new BeginnerProjectService(mapper,
                mock(BeginnerGithubGateway.class), new BeginnerProjectScorer(), new ObjectMapper(),
                mock(ExternalAiClient.class), mock(AiRuntimeConfigService.class),
                mock(TranslationCacheMapper.class), new BeginnerEditorialPolicy());

        assertEquals(List.of("owner/project"), service.recommend(
                List.of("AI智能体"), List.of("Java"), 3).stream().map(item -> item.fullName()).toList());
        verify(mapper, never()).selectRecommended(List.of(), List.of(), 3);
    }

    @Test
    void manualWeeklyPicksPreserveTheRequestedOrder() {
        BeginnerProjectMapper mapper = mock(BeginnerProjectMapper.class);
        BeginnerProjectService service = service(mapper);
        BeginnerProject first = publishableProject(11L, "owner/first");
        BeginnerProject second = publishableProject(12L, "owner/second");
        BeginnerProject third = publishableProject(13L, "owner/third");
        when(mapper.selectById(11L)).thenReturn(first);
        when(mapper.selectById(12L)).thenReturn(second);
        when(mapper.selectById(13L)).thenReturn(third);

        var result = service.replaceWeeklyPicks(List.of(12L, 11L, 13L));

        assertEquals(List.of("owner/second", "owner/first", "owner/third"),
                result.projects().stream().map(item -> item.fullName()).toList());
        verify(mapper).insertWeeklyPick(any(), eq(12L), eq(1), eq("ADMIN"));
        verify(mapper).insertWeeklyPick(any(), eq(11L), eq(2), eq("ADMIN"));
        verify(mapper).insertWeeklyPick(any(), eq(13L), eq(3), eq("ADMIN"));
    }

    @Test
    void manualWeeklyPicksRejectDuplicates() {
        BeginnerProjectMapper mapper = mock(BeginnerProjectMapper.class);
        assertThrows(RuntimeException.class,
                () -> service(mapper).replaceWeeklyPicks(List.of(11L, 11L, 12L)));
        verify(mapper, never()).deleteWeeklyPicks(any());
    }

    @Test
    void administratorCanPublishAProjectBelowTheAutomaticThreshold() {
        BeginnerProjectMapper mapper = mock(BeginnerProjectMapper.class);
        BeginnerProject candidate = new BeginnerProject();
        candidate.setId(21L);
        candidate.setFullName("owner/manual-choice");
        candidate.setStatus("CANDIDATE");
        candidate.setBeginnerScore(20);
        when(mapper.selectById(21L)).thenReturn(candidate);

        service(mapper).updateStatus(21L, "PUBLISHED", null);

        verify(mapper).updateStatus(21L, "PUBLISHED", null);
    }

    @Test
    void administratorCanPickManuallyPublishedProjectsRegardlessOfScores() {
        BeginnerProjectMapper mapper = mock(BeginnerProjectMapper.class);
        BeginnerProject first = manuallyPublishedProject(31L, "owner/first");
        BeginnerProject second = manuallyPublishedProject(32L, "owner/second");
        BeginnerProject third = manuallyPublishedProject(33L, "owner/third");
        when(mapper.selectById(31L)).thenReturn(first);
        when(mapper.selectById(32L)).thenReturn(second);
        when(mapper.selectById(33L)).thenReturn(third);

        var result = service(mapper).replaceWeeklyPicks(List.of(31L, 32L, 33L));

        assertEquals(3, result.projects().size());
        verify(mapper, times(3)).insertWeeklyPick(any(), anyLong(), anyInt(), eq("ADMIN"));
    }

    @Test
    void automaticRefreshDoesNotOverwriteACompleteManualSelection() {
        BeginnerProjectMapper mapper = mock(BeginnerProjectMapper.class);
        List<BeginnerProject> current = List.of(publishableProject(1L, "a/one"),
                publishableProject(2L, "b/two"), publishableProject(3L, "c/three"));
        when(mapper.selectWeeklyPickSource(any())).thenReturn("ADMIN");
        when(mapper.selectWeeklyPicks(any())).thenReturn(current);

        List<String> names = service(mapper).rebuildWeeklyPicks("ALGORITHM")
                .stream().map(item -> item.fullName()).toList();

        assertEquals(List.of("a/one", "b/two", "c/three"), names);
        verify(mapper, never()).deleteWeeklyPicks(any());
        verify(mapper, never()).selectTopForWeekly(anyInt());
    }

    private BeginnerProjectService service(BeginnerProjectMapper mapper) {
        return new BeginnerProjectService(mapper,
                mock(BeginnerGithubGateway.class), new BeginnerProjectScorer(), new ObjectMapper(),
                mock(ExternalAiClient.class), mock(AiRuntimeConfigService.class),
                mock(TranslationCacheMapper.class), new BeginnerEditorialPolicy());
    }

    private BeginnerProject publishableProject(long id, String fullName) {
        BeginnerProject project = new BeginnerProject();
        project.setId(id);
        project.setFullName(fullName);
        project.setOwnerName(fullName.substring(0, fullName.indexOf('/')));
        project.setRepositoryName(fullName.substring(fullName.indexOf('/') + 1));
        project.setHtmlUrl("https://github.com/" + fullName);
        project.setTopicsJson("[]");
        project.setStatus("PUBLISHED");
        project.setBeginnerScore(85);
        project.setVibeCodingFit(5);
        project.setShowcaseValue(5);
        project.setExtensionPotential(4);
        project.setScopeClarity(4);
        project.setContentPotential(4);
        project.setEditorialScore(88);
        return project;
    }

    private BeginnerProject manuallyPublishedProject(long id, String fullName) {
        BeginnerProject project = publishableProject(id, fullName);
        project.setBeginnerScore(10);
        project.setVibeCodingFit(null);
        project.setShowcaseValue(null);
        project.setExtensionPotential(null);
        project.setScopeClarity(null);
        project.setContentPotential(null);
        project.setEditorialScore(null);
        return project;
    }
}
