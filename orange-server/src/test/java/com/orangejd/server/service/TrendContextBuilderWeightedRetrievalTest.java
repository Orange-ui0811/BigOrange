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
    void beginnerPreferenceBoostsUnifiedResultsWithoutFilteringGeneralKnowledge() {
        VectorStore store = mock(VectorStore.class);
        Document general = document("general", "通用方法论", "MANUAL", "通用学习方法");
        Document beginner = document("beginner", "新手项目", "BEGINNER_PROJECT", "新手真实项目案例");
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
        return new Document(id, text, metadata);
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
