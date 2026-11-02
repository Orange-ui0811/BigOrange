package com.orangejd.server.service;

import com.orangejd.server.OrangeApplication;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

@SpringBootTest(classes = OrangeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "RUN_LIVE_RAG_SMOKE", matches = "true")
class RagSmokeTest {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private TrendContextBuilder trendContextBuilder;

    @Test
    void similaritySearchThreeTimes() {
        String question = "有没有适合 Java 初学者、两周内可以完成的项目？";

        for (int round = 1; round <= 3; round++) {
            long started = System.nanoTime();
            List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(question)
                    .topK(3)
                    .similarityThreshold(0.0)
                    .build());
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;

            System.out.println("RAG_ROUND=" + round);
            System.out.println("RAG_ELAPSED_MS=" + elapsedMs);
            System.out.println("RAG_COUNT=" + documents.size());
            for (int index = 0; index < documents.size(); index++) {
                Document document = documents.get(index);
                System.out.println("RAG_DOC_" + index + "=" + document.getText().replace("\n", " "));
                System.out.println("RAG_META_" + index + "=" + document.getMetadata());
                System.out.println("RAG_SCORE_" + index + "=" + document.getScore());
            }
        }

        TrendAnalysisInput input = new TrendAnalysisInput(
                List.of("Java"), "学习并完成项目", "初学者", List.of("Java"), "两周",
                "", "适合 Java 初学者、两周内可以完成的项目", "", "", "",
                true, false, true, false);
        TrendContextBuilder.Retrieval retrieval = trendContextBuilder.retrieve(input);
        System.out.println("RAG_RETRIEVAL_TRACE=" + retrieval.trace());
        System.out.println("RAG_RETRIEVAL_TEXT=" + retrieval.text().replace("\n", " "));
    }
}
