package com.orangejd.server.ragb.retrieval;

import com.orangejd.server.ragb.service.RagBSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagBDocumentRetrieverTest {
    @Test
    void mapsSpringAiQueryContextAndPreservesEvidenceLocation() {
        RagBSearchService search = mock(RagBSearchService.class);
        RagBSearchHit hit = new RagBSearchHit(7, 11, 13, 2, "README", "GITHUB", "owner/repo",
                "部署 > Docker", 120, 320, "证据内容", 0.87, "VECTOR_EXACT");
        when(search.retrieve("如何部署", 5, 0.6, Set.of("GITHUB")))
                .thenReturn(new RagBSearchService.SearchResult("bootstrap", 1, 1, Map.of(), List.of(hit)));

        Query query = Query.builder().text("如何部署").context(Map.of(
                RagBDocumentRetriever.TOP_K, 5,
                RagBDocumentRetriever.SIMILARITY_THRESHOLD, 0.6,
                RagBDocumentRetriever.SOURCE_TYPES, List.of("GITHUB"))).build();
        List<Document> result = new RagBDocumentRetriever(search).retrieve(query);

        verify(search).retrieve("如何部署", 5, 0.6, Set.of("GITHUB"));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo("rag-b-13");
        assertThat(result.getFirst().getMetadata()).containsEntry("sectionPath", "部署 > Docker")
                .containsEntry("startOffset", 120).containsEntry("endOffset", 320);
    }
}
