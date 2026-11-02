package com.orangejd.server.ragb.ingest;

import com.orangejd.server.ragb.domain.RagBDocument;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagBMetadataExtractorTest {
    @Test
    void extractsOnlyExplicitProjectConstraints() {
        RagBDocument document = new RagBDocument();
        document.setSourceType("BEGINNER_PROJECT");
        document.setSourceRef("beginner:42");
        String content = """
                # 示例项目

                ## 主要语言
                Java

                ## 技术栈
                Spring Boot、Redis

                ## 难度
                入门

                ## 预计周期
                2～5 天做出定制版本

                ## 环境要求
                Docker、数据库
                """;

        Map<String, Object> metadata = new RagBMetadataExtractor().extract(document, content);

        assertThat(metadata).containsEntry("language", "Java")
                .containsEntry("durationMaxDays", 5)
                .containsEntry("requiresDocker", true)
                .containsEntry("requiresDatabase", true)
                .containsEntry("requiresSecrets", false)
                .containsEntry("businessId", "42");
    }

    @Test
    void leavesUnknownDurationAndRequirementsAbsent() {
        RagBDocument document = new RagBDocument();
        document.setSourceType("MANUAL");
        document.setSourceRef("manual:test");
        Map<String, Object> metadata = new RagBMetadataExtractor().extract(document, "# 普通文档\n只有正文");
        assertThat(metadata).doesNotContainKeys("durationMaxDays", "requirementsKnown");
    }

    @Test
    void authoritativeBusinessMetadataOverridesIncompleteDocumentSections() {
        RagBDocument document = new RagBDocument();
        document.setSourceType("BEGINNER_PROJECT");
        document.setSourceRef("beginner:42");

        Map<String, Object> metadata = new RagBMetadataExtractor().extract(document, "# 普通介绍",
                Map.of("language", "TypeScript", "duration", "1～2 周", "requiresDocker", false,
                        "requiresDatabase", false, "requiresSecrets", false, "requirementsKnown", true));

        assertThat(metadata).containsEntry("language", "TypeScript")
                .containsEntry("durationMaxDays", 14)
                .containsEntry("requiresDocker", false)
                .containsEntry("requirementsKnown", true);
    }
}
