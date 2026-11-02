package com.orangejd.server.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrendEvidenceCardFormatterTest {
    @Test
    void enforcesPerCardAndRequestBudgetsWithoutLosingCandidateIdentity() {
        List<Document> documents = List.of(
                document("one", "RAG知识库项目", "RAG文档解析、向量检索与问答能力。".repeat(40)),
                document("two", "Agent工具项目", "Agent工具注册、调用和执行治理。".repeat(40)),
                document("three", "Python分析项目", "Python数据清洗、统计和交互式可视化。".repeat(40)));

        var rendered = TrendEvidenceCardFormatter.render(documents);

        assertEquals(3, rendered.cards().size());
        assertTrue(rendered.text().length() <= TrendEvidenceCardFormatter.MAX_CONTEXT_CHARS);
        for (int index = 0; index < rendered.cards().size(); index++) {
            String card = rendered.cards().get(index);
            int cardBudget = "RAW_FALLBACK".equals(rendered.modes().get(index))
                    ? TrendEvidenceCardFormatter.MAX_FALLBACK_CARD_CHARS
                    : TrendEvidenceCardFormatter.MAX_CARD_CHARS;
            assertTrue(card.length() <= cardBudget);
            assertTrue(card.contains("candidateId=E" + (index + 1)));
            assertTrue(card.contains("evidenceId=E" + (index + 1)));
            assertTrue(card.contains("repo=test/"));
        }
    }

    @Test
    void removesUrlsAndMarkdownFromEvidenceButKeepsCanonicalRepositorySlug() {
        Document document = document("safe", "owner/repo|项目", "# 能力\n支持 **RAG**。参考 https://evil.example/a");

        String card = TrendEvidenceCardFormatter.card(document, 1);

        assertTrue(card.contains("repo=test/safe"));
        assertTrue(card.contains("支持 RAG"));
        assertFalse(card.contains("evil.example"));
        assertFalse(card.contains("**"));
    }

    @Test
    void prioritizesStructuredSummaryStackAndCapabilitiesOverPreamble() {
        Document document = document("structured", "项目", "无关构建徽章和赞助信息。".repeat(20)
                + "\n## 项目摘要\nAgent工具聚合网关。\n## 技术栈\nJava,Redis\n"
                + "## 可证明能力\n工具注册、路由和执行治理。\n## 其他\n很长的附录");

        String card = TrendEvidenceCardFormatter.card(document, 1);

        assertTrue(card.contains("摘要:Agent工具聚合网关"));
        assertTrue(card.contains("技术:Java,Redis"));
        assertTrue(card.contains("能力:工具注册、路由和执行治理"));
        assertFalse(card.contains("赞助信息"));
    }

    @Test
    void selectsInformativeReadmeSentenceInsteadOfBuildBadges() {
        Document document = document("slicer", "Slic3r", "构建服务器由赞助商提供。立即获取最新构建！\n"
                + "### 那么，Slic3r是什么？\nSlic3r是用于3D打印机的工具路径生成器，将STL模型转换为G-code指令。");

        String card = TrendEvidenceCardFormatter.card(document, 1);

        assertTrue(card.contains("工具路径生成器"));
        assertTrue(card.contains("G-code"));
        assertFalse(card.contains("赞助商"));
    }

    @Test
    void fallsBackToBoundedCleanRawEvidenceWhenOnlyShortTechnologyBadgesRemain() {
        Document document = document("escaping-notes", "Escaping Notes", "<div align=\"center\">\n"
                + "# Escaping Notes · 逃逸笔记\n**多普勒坠入 · 星轨下的长曝光笔记**\n"
                + "[![Vue 3](https://img.shields.io/badge/Vue-3.x)](https://vuejs.org/)\n"
                + "[![Vite](https://img.shields.io/badge/Vite-5)](https://vitejs.dev/)\n"
                + "[![Canvas 2D](https://img.shields.io/badge/Canvas-2D)");

        var rendered = TrendEvidenceCardFormatter.render(List.of(document));

        assertEquals(1, rendered.fallbackCount());
        assertEquals("RAW_FALLBACK", rendered.modes().getFirst());
        assertTrue(rendered.text().contains("mode=RAW_FALLBACK"));
        assertTrue(rendered.text().contains("Vue 3"));
        assertTrue(rendered.text().contains("Vite"));
        assertTrue(rendered.text().contains("Canvas 2D"));
        assertTrue(rendered.text().length() <= TrendEvidenceCardFormatter.MAX_FALLBACK_CARD_CHARS);
        assertFalse(rendered.text().contains("img.shields.io"));
    }

    @Test
    void compactOnlyModeKeepsOldBudgetForControlledAblation() {
        Document document = document("escaping-notes", "Escaping Notes", "# Escaping Notes\n"
                + "[![Vue 3](https://img.shields.io/badge/Vue-3.x)](https://vuejs.org/)\n"
                + "[![Canvas 2D](https://img.shields.io/badge/Canvas-2D)");

        var rendered = TrendEvidenceCardFormatter.renderCompactOnly(List.of(document));

        assertEquals(0, rendered.fallbackCount());
        assertEquals("CARD", rendered.modes().getFirst());
        assertTrue(rendered.text().length() <= TrendEvidenceCardFormatter.MAX_CARD_CHARS);
    }

    private Document document(String id, String title, String content) {
        return new Document(id, content, Map.of(
                "docId", id,
                "chunkIndex", 0,
                "docTitle", title,
                "source", "GITHUB",
                "repositoryUrl", "https://github.com/test/" + id));
    }
}
