package com.orangejd.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.properties.JwtProperties;
import com.orangejd.pojo.dto.TrendProfileDTO;
import com.orangejd.pojo.entity.TrendHistory;
import com.orangejd.server.controller.app.AppTrendController;
import com.orangejd.server.mapper.TrendHistoryMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Characterization probes: JUnit success means probe ran, not that every acceptance criterion passed. */
class TrendPlatformChainProbeTest {
    static final List<Map<String,Object>> findings=new ArrayList<>();
    static final ObjectMapper JSON=new ObjectMapper();
    @AfterEach void cleanup(){BaseContext.clear();}
    @AfterAll static void report() throws Exception {
        Path path=Path.of("target/trend-platform-chain-probes.json");Files.createDirectories(path.getParent());
        Files.writeString(path,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "scope","In-process real controller/profile facade/analyzer/context/prompt/parser/assembler/binder/history-service; mocked model, vector, graph, quota and DB. No outbound AI calls or real accounts.",
                "findings",findings)));
    }
    void record(String id,boolean passed,Object observed){findings.add(Map.of("id",id,"criterionPassed",passed,"observed",observed));}
    @SuppressWarnings("unchecked") static <T> ObjectProvider<T> provider(T value){
        ObjectProvider<T> p=mock(ObjectProvider.class);when(p.getIfAvailable()).thenReturn(value);return p;
    }
    @SuppressWarnings("unchecked")
    Fixture fixture(boolean evidence,boolean failure,boolean persona) throws Exception {
        BaseContext.setCurrentId(900001L);
        VectorStore store=mock(VectorStore.class);
        Document doc=new Document("synthetic-1","Spring 知识库示例，主要语言 Python。支持上传与语义检索。",
                Map.of("docTitle","acceptance/synthetic","source","GITHUB","sourceRef","acceptance/synthetic","repositoryUrl","https://github.com/acceptance/synthetic"));
        if(failure)when(store.similaritySearch(any(SearchRequest.class))).thenThrow(new IllegalStateException("synthetic retrieval failure"));
        else when(store.similaritySearch(any(SearchRequest.class))).thenReturn(evidence?List.of(doc):List.of());
        PersonaPromptContextService personas=mock(PersonaPromptContextService.class);
        when(personas.confirmedContext(900001L)).thenReturn("已确认画像：希望学习 Spring，只推荐 Java 项目。");
        TrendContextBuilder context=new TrendContextBuilder(provider(store),provider(personas));
        KnowledgeGraphService graph=mock(KnowledgeGraphService.class);
        when(graph.buildCompareGraph()).thenReturn(Map.of("thisWeek",Map.of("nodes",List.of()),"lastWeek",Map.of("nodes",List.of())));
        QuotaService quota=mock(QuotaService.class);when(quota.consume(eq(900001L),anyString(),anyString(),any(QuotaRequestIdentity.class))).thenReturn(new QuotaCharge(950,50,1,null,false));
        when(quota.getBalance(900001L)).thenReturn(950L);
        TrendModelClient model=mock(TrendModelClient.class);
        when(model.completeMeasured(any(),any())).thenReturn(new TrendModelClient.Completion(answer(evidence?"E1":"E999"),Map.of("usageSource","TEST")));
        TrendResultAssembler assembler=new TrendResultAssembler(new TrendResponseParser(),mock(TrendRecommendationSanitizer.class),new TrendProjectMatchExplainer(),JSON);
        TrendV2Analyzer analyzer=new TrendV2Analyzer(graph,quota,mock(PricingService.class),provider(null),provider(null),context,new TrendPromptFactory(),model,assembler);
        TrendAnalysisService facade=new TrendAnalysisService(provider(null),provider(null),mock(TrendV1Analyzer.class),analyzer,"https://synthetic.invalid/v1","not-a-real-key","synthetic");
        TrendHistoryMapper history=mock(TrendHistoryMapper.class);
        doAnswer(call->{((TrendHistory)call.getArgument(0)).setId(900002L);return 1;}).when(history).insert(any());
        TrendProfileAnalysisService profiles=new TrendProfileAnalysisService(facade,history,mock(PricingService.class),JSON);
        AppTrendController controller=new AppTrendController(graph,facade,profiles,new TrendHistoryService(history),JSON,mock(AiRecordMetrics.class),mock(JwtProperties.class),null);
        TrendProfileDTO input=new TrendProfileDTO();input.setTopics(persona?List.of():List.of("Spring"));input.setPurpose("学习");input.setExperience("新手");
        input.setLanguages(List.of("Java"));input.setTimeCommitment("两周");input.setCustomPurpose("必须只推荐 Java 项目");input.setUseRag(true);input.setUsePersona(persona);
        return new Fixture(controller,input,store,model,quota,history);
    }
    @SuppressWarnings("unchecked") Map<String,Object> run(Fixture f){return (Map<String,Object>)f.controller.analyzeV2(f.input,mock(HttpServletRequest.class)).getData();}
    static String answer(String candidate) throws Exception {
        return JSON.writeValueAsString(Map.of("analysis",Map.of("trend","测试趋势","prospect","测试前景","demand","测试需求","summary","测试总结"),
                "learning",Map.of("timeline","两周","steps",List.of("运行示例")),"recommendations",List.of(Map.of(
                "candidateId",candidate,"name","Python 示例","description","主要使用 Python 的 Spring 相关示例。","difficulty","入门","difficultyReason","测试基础",
                "vibeCodingReason","测试辅助开发","recommendReason","测试推荐理由","prerequisites","Python","score",80))));
    }
    @Test void followsControllerThroughHistoryAndBindsCandidate() throws Exception {
        Fixture f=fixture(true,false,false);Map<String,Object> result=run(f);
        assertEquals(true,result.get("historySaved"));verify(f.history).insert(any());verify(f.quota).consume(eq(900001L),anyString(),anyString(),any(QuotaRequestIdentity.class));
        List<?> recommendations=(List<?>)result.get("recommendations");assertEquals(1,recommendations.size());
        Map<?,?> recommendation=(Map<?,?>)recommendations.getFirst();assertEquals("VERIFIED_CANDIDATE",recommendation.get("evidenceStatus"));
        record("controller_to_history_and_candidate_binding",true,Map.of("historySaved",true,"evidenceStatus",recommendation.get("evidenceStatus")));
        record("explicit_java_constraint_enforced_after_generation",recommendations.isEmpty(),recommendation.get("description"));
        record("b_used_in_final_platform_chain",false,result.get("retrievalTrace"));
    }
    @Test void emptyEvidenceProbe() throws Exception {
        Fixture f=fixture(false,false,false);Map<String,Object> result=run(f);assertTrue(result.containsKey("recommendations"));
        List<?> items=(List<?>)result.get("recommendations");record("empty_evidence_forces_no_project_recommendation",items.isEmpty(),result);
    }
    @Test void retrievalFailureProbe() throws Exception {
        Fixture f=fixture(false,true,false);Map<String,Object> result=run(f);assertTrue(result.containsKey("retrievalTrace"));
        record("retrieval_failure_does_not_return_unsupported_project",((List<?>)result.get("recommendations")).isEmpty(),result);
    }
    @Test void personaOnlyProbe() throws Exception {
        Fixture f=fixture(true,false,true);Map<String,Object> result=run(f);verifyNoInteractions(f.store);
        var trace=(Map<?,?>)result.get("retrievalTrace");record("confirmed_persona_can_drive_retrieval_without_duplicate_form",!"NO_TOPIC".equals(trace.get("status")),trace);
    }
    @Test void malformedGenerationRefundsAndDoesNotSaveHistory() throws Exception {
        Fixture f=fixture(true,false,false);
        when(f.model.completeMeasured(any(),any())).thenReturn(new TrendModelClient.Completion("{broken",Map.of()));
        assertThrows(com.orangejd.common.exception.BaseException.class,()->run(f));
        verify(f.quota).refund(eq(900001L),any(),any());verify(f.history,never()).insert(any());
        record("invalid_json_refunds_and_skips_history",true,"refund called; no history insertion");
    }
    record Fixture(AppTrendController controller,TrendProfileDTO input,VectorStore store,TrendModelClient model,QuotaService quota,TrendHistoryMapper history){}
}
