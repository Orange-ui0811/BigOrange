package com.orangejd.server.service;
import com.orangejd.pojo.dto.TrendProfileDTO;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;
import com.orangejd.server.ragb.retrieval.*;
import com.orangejd.server.ragb.service.RagBSearchService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class TrendRagABPreviewServiceTest {
    final RagBSearchService b=mock(RagBSearchService.class);
    final TrendV2Analyzer analyzer=mock(TrendV2Analyzer.class);
    final TrendRagABPreviewService service=new TrendRagABPreviewService(mock(TrendContextBuilder.class),b,analyzer,mock(AiRuntimeConfigService.class));
    @Test void refusesAccountProfilesAndInvalidVariantsBeforeCalls() {
        var p=new TrendProfileDTO();p.setUsePersona(true);
        assertThrows(IllegalArgumentException.class,()->service.preview("B",p,true));
        assertThrows(IllegalArgumentException.class,()->service.preview("C",new TrendProfileDTO(),true));
        verifyNoInteractions(b,analyzer);
    }
    @Test void noTopicDoesNotCallEmbeddingOrInventEvidence() {
        var result=service.retrieveB(new TrendAnalysisInput(List.of(),null,null,List.of(),null,null,null,null,null,null,true,false,false,false));
        assertEquals("NO_TOPIC",result.trace().get("status"));verifyNoInteractions(b);
    }
    @Test void aggregatesRepositoryPassagesAndKeepsSamePromptBudget() {
        var one=hit(1,"owner/rag","RAG 知识库项目，支持文档上传、检索和引用。".repeat(30));
        var two=hit(2,"owner/rag","RAG 项目部署支持 Docker 和 MySQL。".repeat(30));
        var other=hit(3,"owner/another","RAG 平台支持知识问答和向量索引。".repeat(30));
        when(b.retrieve(anyString(),eq(8),eq(.55),any(RagBSearchConstraints.class))).thenReturn(
                new RagBSearchService.SearchResult("m3","HNSW_HYBRID",3,3,Map.of(),List.of(one,two,other),List.of(),"ANSWER_WITH_CITATIONS"));
        var result=service.retrieveB(new TrendAnalysisInput(List.of("RAG"),"学习","新手",List.of("Java"),"半年",null,null,null,null,null,true,false,false,false));
        assertTrue(result.text().length()<=1100);
        var evidence=(List<?>)result.trace().get("evidence");assertEquals(2,evidence.size());
        assertEquals(2,((List<?>)((Map<?,?>)evidence.getFirst()).get("chunks")).size());
        assertEquals("https://github.com/owner/rag",((Map<?,?>)evidence.getFirst()).get("repositoryUrl"));
        verify(b,times(2)).retrieve(anyString(),eq(8),eq(.55),any(RagBSearchConstraints.class));
    }
    static RagBSearchHit hit(long id,String repo,String text) {
        return new RagBSearchHit(1,1,id,(int)id,repo,"GITHUB",repo,"README",0,text.length(),text,.8,"HYBRID_RRF");
    }
}
