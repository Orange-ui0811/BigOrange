package com.orangejd.server.service;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class TrendTopicGateTest {
    private TrendAnalysisInput input(String topic) {
        return new TrendAnalysisInput(List.of(topic), "学习", "新手", List.of("Python"), "5h",
                "", "", "", "", "", true, false, false, false);
    }
    @Test void rejectsHighSimilarityButDifferentTechnology() {
        assertFalse(TrendTopicGate.accepts(new Document("React前端设计工具"), input("Spring Boot后端开发")));
        assertTrue(TrendTopicGate.accepts(new Document("Spring Boot REST API"), input("Spring Boot后端开发")));
        assertFalse(TrendTopicGate.accepts(new Document("JavaScript前端代码"), input("Python数据分析")));
        assertTrue(TrendTopicGate.accepts(new Document("使用 pandas 做数据分析"), input("Python数据分析")));
    }
    @Test void supportsChineseAliasesAndWholeLatinTokens() {
        assertTrue(TrendTopicGate.accepts(new Document("本地知识库问答"), input("RAG问答")));
        assertTrue(TrendTopicGate.accepts(new Document("智能体工具调用"), input("AI Agent")));
        assertFalse(TrendTopicGate.accepts(new Document("Vuex alternatives without framework details"), input("Vue")));
    }
    @Test void doesNotTreatTheLetterIn3dOrGenericOpenSourceTextAsAHit() {
        assertFalse(TrendTopicGate.accepts(new Document("A verified open source AI project with repository URL"),
                input("3D打印开源项目")));
        assertFalse(TrendTopicGate.accepts(new Document("这是一个热门开源项目"), input("3D打印开源项目")));
        assertFalse(TrendTopicGate.accepts(new Document("Free C++ STL embedded development tools"),
                input("3D打印开源项目")));
        assertFalse(TrendTopicGate.accepts(new Document("3D Slicer medical image visualization and analysis"),
                input("3D打印开源项目")));
        assertTrue(TrendTopicGate.accepts(new Document("3D printer slicer with STL and G-code support"),
                input("3D打印开源项目")));
    }
    @Test void canonicalRepositoryTitleUnifiesDifferentCatalogDocuments() {
        var first = new Document("1", "body", java.util.Map.of("docTitle", "Owner/Repo|week", "docId", "1"));
        var second = new Document("2", "body", java.util.Map.of("docTitle", "owner/repo", "docId", "2"));
        assertEquals(TrendTopicGate.projectKey(first), TrendTopicGate.projectKey(second));
    }
}
