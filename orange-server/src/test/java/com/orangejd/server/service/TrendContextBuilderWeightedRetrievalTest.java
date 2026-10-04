package com.orangejd.server.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TrendContextBuilderWeightedRetrievalTest {
    @Test
    void keepsOnlyOneChunkPerDocumentAndAtMostOneJobPosting() {
        VectorStore store = mock(VectorStore.class);
        Document first = new Document("a", "Agent方案一", Map.of("docId", "same", "chunkIndex", 0,
                "source", "GITHUB", "repositoryUrl", "https://github.com/test/same"));
        Document second = new Document("b", "Agent方案二", Map.of("docId", "same", "chunkIndex", 1,
                "source", "GITHUB", "repositoryUrl", "https://github.com/test/same"));
        Document job1 = document("job1", "Agent岗位一", "JOB_POSTING", "Agent岗位要求一");
        Document job2 = document("job2", "Agent岗位二", "JOB_POSTING", "Agent岗位要求二");
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(first, second, job1, job2));
        var input = new TrendAnalysisInput(List.of("Agent"), "求职", "中级", List.of("Python"), "5h",
                "", "", "", "", "", true, false, false, false);
        var retrieval = new TrendContextBuilder(provider(store), provider(null)).retrieve(input);
        org.junit.jupiter.api.Assertions.assertEquals(2, ((List<?>)retrieval.trace().get("evidence")).size());
    }
    @Test
    void traceKeepsRawEvidenceWhilePromptUsesCompressedCard() {
        VectorStore store = mock(VectorStore.class);
        String raw = "Agent训练实验项目".repeat(80) + " https://github.com/a/b";
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                document("training", "Agent模型训练", "GITHUB", raw)));
        TrendAnalysisInput input = new TrendAnalysisInput(List.of("Agent"), "学习", "中级", List.of("Python"),
                "5h", "", "", "", "", "", true, false, false, false);
        var result = new TrendContextBuilder(provider(store), provider(null)).retrieve(input);
        assertTrue(result.text().contains("evidenceId=E1"));
        assertTrue(result.text().contains("repo=test/training"));
        assertTrue(result.text().length() <= TrendEvidenceCardFormatter.MAX_CARD_CHARS);
        assertTrue(!result.text().contains(raw));
        org.junit.jupiter.api.Assertions.assertEquals("USED", result.trace().get("status"));
        org.junit.jupiter.api.Assertions.assertEquals("CARD_V2_ADAPTIVE", result.trace().get("evidenceFormat"));
        List<?> evidence = (List<?>) result.trace().get("evidence");
        org.junit.jupiter.api.Assertions.assertEquals(1, evidence.size());
        org.junit.jupiter.api.Assertions.assertEquals(raw, ((Map<?, ?>) evidence.getFirst()).get("text"));
        org.junit.jupiter.api.Assertions.assertEquals(result.text(), ((Map<?, ?>) evidence.getFirst()).get("card"));
        assertTrue(((Number) result.trace().get("evidenceCompressionRatio")).doubleValue() > .5);
    }
    @Test
    void secondaryRouteCannotIntroduceOffTopicCareerProject() {
        VectorStore store = mock(VectorStore.class);
        Document training = document("training", "模型训练", "GITHUB", "训练实验项目");
        Document unrelated = document("gui", "GUI代理", "CAREER_PROJECT", "屏幕操作项目");
        when(store.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(training)).thenReturn(List.of(unrelated, training));
        TrendAnalysisInput input = new TrendAnalysisInput(List.of("模型训练"), "求职", "中级",
                List.of("Python"), "每周5小时", "", "", "", "", "", true, false, false, true);
        String result = new TrendContextBuilder(provider(store), provider(null)).rag(input);
        assertTrue(result.contains("训练实验项目"));
        assertTrue(!result.contains("屏幕操作项目"));
        assertTrue(TrendContextBuilder.retrievalQueries(input).stream()
                .allMatch(query -> query.contains("模型训练") && query.contains("Python")));
    }

    @Test
    void emptyTopicDoesNotIssueGenericSearches() {
        VectorStore store = mock(VectorStore.class);
        TrendAnalysisInput input = new TrendAnalysisInput(List.of(), "求职", "中级", List.of(),
                "每周5小时", "", "", "", "", "", true, false, false, false);
        assertTrue(new TrendContextBuilder(provider(store), provider(null)).rag(input).isEmpty());
        verifyNoInteractions(store);
    }

    @Test
    void beginnerPreferenceBoostsUnifiedResultsWithoutFilteringGeneralKnowledge() {
        VectorStore store = mock(VectorStore.class);
        Document general = document("general", "Agent方法论", "MANUAL", "Agent通用学习方法");
        Document beginner = document("beginner", "Agent新手项目", "BEGINNER_PROJECT", "Agent新手真实项目案例");
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(general, beginner));
        TrendContextBuilder builder = new TrendContextBuilder(provider(store), provider(null));
        TrendAnalysisInput input = new TrendAnalysisInput(List.of("Agent"), "学习", "新手", List.of("Java"),
                "每周5小时", "", "", "", "", "", true, false, true, false);

        String result = builder.rag(input);

        assertTrue(result.indexOf("新手真实项目案例") < result.indexOf("通用学习方法"));
        assertTrue(result.contains("通用学习方法"));
    }

    @Test
    void personaContextIsOnlyIncludedAfterExplicitOptIn() {
        PersonaPromptContextService persona = mock(PersonaPromptContextService.class);
        when(persona.confirmedContext(9L)).thenReturn("已确认画像：希望学习 Agent");
        TrendContextBuilder builder = new TrendContextBuilder(provider(null), provider(persona));
        TrendAnalysisInput disabled = new TrendAnalysisInput(List.of("Agent"), "学习", "新手", List.of("Java"),
                "每周5小时", "", "", "", "", "", false, false, false, false);
        TrendAnalysisInput enabled = new TrendAnalysisInput(List.of("Agent"), "学习", "新手", List.of("Java"),
                "每周5小时", "", "", "", "", "", false, true, false, false);

        String withoutPersona = builder.profile(disabled, 9L);
        verifyNoInteractions(persona);
        String withPersona = builder.profile(enabled, 9L);

        assertTrue(!withoutPersona.contains("已确认画像"));
        assertTrue(withPersona.contains("已确认画像"));
        verify(persona).confirmedContext(9L);
    }

    private Document document(String id, String title, String source, String text) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("docId", id);
        metadata.put("chunkIndex", 0);
        metadata.put("docTitle", title);
        metadata.put("source", source);
        metadata.put("repositoryUrl", "https://github.com/test/" + id);
        return new Document(id, text, metadata);
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
