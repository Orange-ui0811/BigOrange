package com.orangejd.server.ragb.service;

import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.ragb.domain.RagBIndexGeneration;
import com.orangejd.server.ragb.mapper.RagBMapper;
import com.orangejd.server.ragb.retrieval.RagBIndexRouter;
import com.orangejd.server.ragb.retrieval.RagBSearchConstraints;
import com.orangejd.server.ragb.retrieval.RagBSearchHit;
import com.orangejd.server.ragb.retrieval.RagBVectorSearchResult;
import com.orangejd.server.ragb.retrieval.RagBConstraintMatcher;
import com.orangejd.server.ragb.retrieval.RagBRetrievalMetadataResolver;
import com.orangejd.server.ragb.retrieval.RagBReranker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagBSearchServiceTest {
    @Test void reranksBeforeTopKWithoutReplacingFusedScores(){
        var f=fixture(List.of(hit(1,11,"GITHUB","acme/one",.9,"first"),hit(2,22,"GITHUB","acme/two",.1,"specific fact")));
        var r=mock(RagBReranker.class);
        when(r.rerank(anyString(),any())).thenAnswer(call->{List<RagBSearchHit> input=call.getArgument(1);assertThat(input).hasSize(2);
            return new RagBReranker.Result(List.of(input.get(1).withRerankScore(.99),input.get(0).withRerankScore(.01)),new RagBReranker.Trace("APPLIED","model",2,2,1,""));});
        var result=f.withReranker(r).retrieve("specific fact",1,.5,Set.of());
        assertThat(result.hits()).extracting(RagBSearchHit::chunkId).containsExactly(200L);assertThat(result.hits().getFirst().score()).isEqualTo(.1);assertThat(result.rerank().status()).isEqualTo("APPLIED");
    }
    @Test void filtersInactiveAndWrongScopeBeforeSendingCandidates(){
        var f=fixture(List.of(hit(1,11,"GITHUB","acme/one",.9,"inactive"),hit(2,22,"GITHUB","acme/two",.8,"allowed"),hit(3,33,"GITHUB","acme/three",.7,"wrong scope")));
        when(f.mapper.selectDocument(1L)).thenReturn(null);var r=mock(RagBReranker.class);
        when(r.rerank(anyString(),any())).thenAnswer(call->{List<RagBSearchHit> input=call.getArgument(1);assertThat(input).extracting(RagBSearchHit::documentId).containsExactly(2L);return new RagBReranker.Result(input,RagBReranker.Trace.disabled(input.size()));});
        var result=f.withReranker(r).retrieve("q",8,.5,RagBSearchConstraints.sources(Set.of()).documents(Set.of(1L,2L)));
        assertThat(result.excludedReasons()).containsEntry("DOCUMENT_MISSING",1).containsEntry("DOCUMENT_SCOPE_MISMATCH",1);
    }
    @Test void deduplicatesBeforeRerankAndKeepsDiversityAfterRerank(){
        var one=hit(1,11,"GITHUB","acme/one",.9,"same");var two=hitWithChunk(1,11,102,"GITHUB","acme/one",.8,"other fact");var three=hit(2,22,"GITHUB","acme/two",.7,"different project");
        var f=fixture(List.of(one,hit(3,33,"GITHUB","acme/one|week",.89,"same"),two,three));var r=mock(RagBReranker.class);
        when(r.rerank(anyString(),any())).thenAnswer(call->{List<RagBSearchHit> input=call.getArgument(1);assertThat(input).hasSize(3);return new RagBReranker.Result(List.of(two.withRerankScore(.9),one.withRerankScore(.8),three.withRerankScore(.7)),new RagBReranker.Trace("APPLIED","model",3,3,1,""));});
        assertThat(f.withReranker(r).retrieve("推荐项目",2,.5,Set.of()).hits()).extracting(RagBSearchHit::chunkId).containsExactly(102L,200L);
    }
    @Test void rerankingCannotBypassFinalPricingEvidenceGuard(){
        var f=fixture(List.of(hit(1,11,"GITHUB","acme/one",.9,"Apache license"),hit(2,22,"GITHUB","acme/two",.8,"MIT license")));var r=mock(RagBReranker.class);
        when(r.rerank(anyString(),any())).thenAnswer(call->new RagBReranker.Result(call.getArgument(1),new RagBReranker.Trace("APPLIED","model",2,2,1,"")));
        assertThat(f.withReranker(r).retrieve("商业授权价格是多少？",8,.5,Set.of()).answerPolicy()).isEqualTo("REFUSE_NO_MATCH");
    }
    @Test
    void marksThinEvidenceAsInsufficient() {
        Fixture fixture = fixture(List.of(hit(1, 11, "MANUAL", "manual:1", 0.8, "short evidence")));

        RagBSearchService.SearchResult result = fixture.service.retrieve("question", 8, 0.5, Set.of());

        assertThat(result.status()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(result.answerPolicy()).isEqualTo("REFUSE_INSUFFICIENT_EVIDENCE");
    }

    @Test
    void beginnerPreferenceChangesRankingWithoutBypassingBusinessState() {
        RagBSearchHit general = hit(1, 11, "MANUAL", "manual:1", 0.80, "general");
        RagBSearchHit beginner = hit(2, 22, "BEGINNER_PROJECT", "beginner:2", 0.79, "beginner");
        Fixture fixture = fixture(List.of(general, beginner));
        when(fixture.mapper.countPublishedBeginnerProject(2L)).thenReturn(1);
        RagBSearchConstraints constraints = new RagBSearchConstraints(Set.of(), Set.of(), null, null,
                null, null, null, true, false);

        RagBSearchService.SearchResult result = fixture.service.retrieve("question", 8, 0.5, constraints);

        assertThat(result.hits()).extracting(RagBSearchHit::documentId).containsExactly(2L, 1L);
        assertThat(result.hits().getFirst().retrievalRoute()).contains("PREFERENCE_BEGINNER");
    }

    @Test
    void refusesPricingQuestionWhenRetrievedDocumentsContainNoPricingEvidence() {
        Fixture fixture = fixture(List.of(hit(1, 11, "MANUAL", "manual:1", 0.8,
                "项目采用 Apache-2.0 开源许可证，支持本地部署。")));

        RagBSearchService.SearchResult result = fixture.service.retrieve("商业授权价格是多少？", 8, 0.5, Set.of());

        assertThat(result.hits()).isEmpty();
        assertThat(result.answerPolicy()).isEqualTo("REFUSE_NO_MATCH");
        assertThat(result.excludedReasons()).containsEntry("PRICING_EVIDENCE_MISSING", 1);
    }

    @Test
    void givesDistinctSourcesAFirstPassBeforeAddingMoreChunksFromOneSource() {
        List<RagBSearchHit> hits = List.of(
                hit(1, 11, "GITHUB", "acme/one", 0.90, "one-a"),
                hitWithChunk(1, 11, 102, "GITHUB", "acme/one", 0.89, "one-b"),
                hitWithChunk(1, 11, 103, "GITHUB", "acme/one", 0.88, "one-c"),
                hit(2, 22, "GITHUB", "acme/two", 0.80, "two"),
                hit(3, 33, "GITHUB", "acme/three", 0.70, "three"));
        Fixture fixture = fixture(hits);

        RagBSearchService.SearchResult result = fixture.service.retrieve("推荐三个项目", 3, 0.5, Set.of());

        assertThat(result.hits()).extracting(RagBSearchHit::sourceRef)
                .containsExactly("acme/one", "acme/two", "acme/three");
    }

    @Test
    void factualQuestionKeepsDistinctPassagesFromTheSameRepository() {
        Fixture fixture = fixture(List.of(
                hit(1, 11, "GITHUB", "acme/one", 0.90, "deployment requirements"),
                hitWithChunk(1, 11, 102, "GITHUB", "acme/one", 0.89, "recovery behavior"),
                hit(2, 22, "GITHUB", "acme/two", 0.80, "other project")));

        var result = fixture.service.retrieve("故障后如何恢复执行？", 2, 0.5, Set.of());

        assertThat(result.hits()).extracting(RagBSearchHit::chunkId).containsExactly(100L, 102L);
        org.mockito.Mockito.verify(fixture.mapper, org.mockito.Mockito.times(1)).selectDocument(1L);
    }
    @Test void explicitProjectDiversityDoesNotDependOnWordsInTheRewrittenQuery() {
        Fixture fixture=fixture(List.of(hit(1,11,"GITHUB","acme/one",.90,"AI application framework"),hitWithChunk(1,11,102,"GITHUB","acme/one",.89,"database deployment details"),hit(2,22,"GITHUB","acme/two",.80,"agent application runtime")));
        var result=fixture.service.retrieve("AI application development",2,.5,RagBSearchConstraints.sources(Set.of("GITHUB")).projectDiversity());
        assertThat(result.hits()).extracting(RagBSearchHit::sourceRef).containsExactly("acme/one","acme/two");
        org.mockito.Mockito.verify(fixture.indexes).searchWithTrace(any(),org.mockito.ArgumentMatchers.eq("AI application development"),anyInt(),anyDouble(),any());
    }

    @Test
    void removesIdenticalPassagesAcrossWeeklySnapshotsButKeepsNewFacts() {
        Fixture fixture = fixture(List.of(
                hit(1, 11, "GITHUB", "acme/one", 0.90, "same evidence"),
                hit(2, 22, "GITHUB", "acme/one|week", 0.89, "same evidence"),
                hit(3, 33, "GITHUB", "acme/one|new", 0.88, "additional evidence")));

        var result = fixture.service.retrieve("功能是什么？", 3, 0.5, Set.of());

        assertThat(result.hits()).extracting(RagBSearchHit::documentId).containsExactly(1L, 3L);
    }
    @Test void selectedDocumentsRemainScopedBeforeTruncationAndAcrossValidation() {
        Fixture f=fixture(List.of(hit(1,11,"GITHUB","acme/unrelated",.99,"unrelated content"),hit(2,22,"GITHUB","acme/selected",.70,"selected deployment content")));
        var result=f.service.retrieve("deployment",1,0,RagBSearchConstraints.sources(Set.of("GITHUB")).documents(Set.of(2L)));
        assertThat(result.hits()).extracting(RagBSearchHit::documentId).containsExactly(2L);
        org.mockito.Mockito.verify(f.indexes).searchWithTrace(any(),anyString(),anyInt(),anyDouble(),org.mockito.ArgumentMatchers.argThat(c->c.documentIds().equals(Set.of(2L))));
    }

    private Fixture fixture(List<RagBSearchHit> hits) {
        RagBProperties properties = new RagBProperties();
        properties.setEnabled(true);
        RagBMapper mapper = mock(RagBMapper.class);
        RagBIndexRouter indexes = mock(RagBIndexRouter.class);
        RagBRetrievalMetadataResolver metadata = mock(RagBRetrievalMetadataResolver.class);
        RagBIndexGeneration generation = new RagBIndexGeneration();
        generation.setId(1L);
        generation.setGenerationKey("active");
        generation.setAdapterType("EXACT");
        generation.setRedisPrefix("orange:rag:b:g:active:");
        when(mapper.selectActiveGeneration()).thenReturn(generation);
        when(indexes.searchWithTrace(any(), anyString(), anyInt(), anyDouble(), any()))
                .thenReturn(new RagBVectorSearchResult(hits, hits.size(), Map.of()));
        when(indexes.adapterType(generation)).thenReturn("EXACT");
        for (RagBSearchHit hit : hits) {
            RagBDocument document = new RagBDocument();
            document.setId(hit.documentId());
            document.setStatus("READY");
            document.setActiveVersionId(hit.versionId());
            when(mapper.selectDocument(hit.documentId())).thenReturn(document);
            when(metadata.resolve(document, hit)).thenReturn(Map.of("sourceType", hit.sourceType(),"documentId",hit.documentId()));
        }
        return new Fixture(new RagBSearchService(properties, mapper, indexes, new RagBRelevanceGuard(),
                new RagBConstraintMatcher(), metadata), mapper,indexes);
    }

    private RagBSearchHit hit(long documentId, long versionId, String sourceType, String sourceRef,
                              double score, String content) {
        return hitWithChunk(documentId, versionId, documentId * 100, sourceType, sourceRef, score, content);
    }

    private RagBSearchHit hitWithChunk(long documentId, long versionId, long chunkId, String sourceType,
                                       String sourceRef, double score, String content) {
        return new RagBSearchHit(documentId, versionId, chunkId, 0, "title", sourceType,
                sourceRef, "Overview", 0, content.length(), content, score, "VECTOR_EXACT");
    }

    private record Fixture(RagBSearchService service, RagBMapper mapper,RagBIndexRouter indexes) {
        RagBSearchService withReranker(RagBReranker reranker){
            // Metadata fixtures are source-only; no additional constraints are inferred.
            RagBRetrievalMetadataResolver metadata=mock(RagBRetrievalMetadataResolver.class);
            when(metadata.resolve(any(),any())).thenAnswer(call->{RagBSearchHit h=call.getArgument(1);return Map.of("sourceType",h.sourceType(),"documentId",h.documentId());});
            var properties=new RagBProperties();properties.setEnabled(true);
            return new RagBSearchService(properties,mapper,indexes,new RagBRelevanceGuard(),new RagBConstraintMatcher(),metadata,reranker);
        }
    }
}
