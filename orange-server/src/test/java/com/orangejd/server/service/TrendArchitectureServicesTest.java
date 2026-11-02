package com.orangejd.server.service;

import com.orangejd.common.context.BaseContext;
import com.orangejd.server.foundation.aitool.AiToolRouter;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrendArchitectureServicesTest {
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    @Test
    void parserPreservesStructuredResultShapeAndCompletenessRules() {
        String json = """
                {"analysis":{"trend":"增长","prospect":"机会","demand":"需求","summary":"总结"},
                "recommendations":[{"name":"项目","description":"说明","difficulty":"入门",
                "difficultyReason":"原因","vibeCodingReason":"适合","recommendReason":"推荐",
                "prerequisites":"基础","references":[{"name":"repo","url":"https://github.com/a/b"}],"score":88}],
                "learning":{"steps":["第一步"],"timeline":"一周","tips":"建议"}}
                """;
        TrendResponseParser parser = new TrendResponseParser();

        Map<String, Object> result = parser.parse(json);

        assertTrue(parser.complete(result));
        assertEquals("增长", ((Map<?, ?>) result.get("analysis")).get("trend"));
        assertEquals(88, ((Map<?, ?>) ((List<?>) result.get("recommendations")).getFirst()).get("score"));
        assertFalse(parser.complete(parser.parse("")));
        assertFalse(parser.complete(Map.of()));
    }

    @Test
    void sanitizerRemovesAnyReferenceNotBackedByVerifiedCases() {
        Map<String, Object> recommendation = new LinkedHashMap<>();
        recommendation.put("references", new ArrayList<>(List.of(
                Map.of("name", "invented", "url", "https://example.com/not-verified"), "legacy")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recommendations", List.of(recommendation));

        new TrendRecommendationSanitizer().sanitize(result, List.of(), List.of());

        assertEquals(List.of(), recommendation.get("references"));
    }

    @Test
    void compatibilityFacadeKeepsRuntimeSourceAndPublicEntryPoints() {
        TrendV1Analyzer v1 = mock(TrendV1Analyzer.class);
        TrendV2Analyzer v2 = mock(TrendV2Analyzer.class);
        when(v1.analyze(any(), any(), any(QuotaRequestIdentity.class), any(), eq(false)))
                .thenReturn(Map.of("content", "ok"));
        when(v2.analyze(any(), any(), any(QuotaRequestIdentity.class), any(), eq(false)))
                .thenReturn(Map.of("analysis", "ok"));
        TrendAnalysisService service = new TrendAnalysisService(provider(null), provider(null), v1, v2,
                "https://model.example/v1", "secret", "model-name");
        BaseContext.setCurrentId(7L);

        assertEquals("ok", service.analyze("question").get("content"));
        service.analyzeV2(List.of("Agent"), "学习", "新手", List.of("Java"), "5h",
                "", "", "", "", "", true, true, true, false);

        verify(v1).analyze(eq("question"), eq(7L), any(QuotaRequestIdentity.class),
                org.mockito.ArgumentMatchers.argThat(runtime -> "model-name".equals(runtime.model())), eq(false));
        verify(v2).analyze(any(), eq(7L), any(QuotaRequestIdentity.class),
                org.mockito.ArgumentMatchers.argThat(runtime -> "ENV".equals(runtime.source())), eq(false));
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
