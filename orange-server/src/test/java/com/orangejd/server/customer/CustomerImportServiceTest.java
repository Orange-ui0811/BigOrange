package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.ragb.mapper.RagBMapper;
import com.orangejd.server.ragb.domain.RagBDocument;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerImportServiceTest {
    CustomerStore store=mock(CustomerStore.class); CustomerProjects projects=mock(CustomerProjects.class); RagBMapper indexes=mock(RagBMapper.class);
    CustomerJson json=new CustomerJson(new ObjectMapper()); CustomerImportService service=new CustomerImportService(store,projects,json,indexes);
    void setup(boolean evidence) {
        Map<String,Object> card=new LinkedHashMap<>(Map.of("type","BEGINNER_PROJECT","id",7,"name","owner/repo","url","https://github.com/owner/repo","importable",evidence,
            "references",evidence?List.of(Map.of("documentId",3,"versionId",8)):List.of()));
        when(store.run("r",5)).thenReturn(Map.of("status","SUCCEEDED","session_id",1,"result_json",json.write(Map.of("projects",List.of(card)))));
        when(store.session(1,5)).thenReturn(Map.of("state_json","{\"language\":\"Java\"}"));
        when(projects.detail("BEGINNER_PROJECT",7,Map.of("language","Java"))).thenReturn(Map.of("name","owner/repo","url","https://github.com/owner/repo"));
        var document=new RagBDocument(); document.setStatus("READY"); document.setActiveVersionId(8L); when(indexes.selectDocument(3L)).thenReturn(document);
    }
    @Test void serverBuildsDraftFromOwnedRunAndCurrentDatabase() { setup(true); assertThat(service.draft("r",5,"BEGINNER_PROJECT",7).get("draft").toString()).contains("https://github.com/owner/repo","Java"); }
    @Test void forgedCandidateIsRejectedBeforeDatabaseLookup() { setup(true); assertThatThrownBy(()->service.draft("r",5,"BEGINNER_PROJECT",99)).hasMessageContaining("409"); verifyNoInteractions(projects); }
    @Test void emptyEvidenceCannotEnterPlan() { setup(false); assertThatThrownBy(()->service.draft("r",5,"BEGINNER_PROJECT",7)).hasMessageContaining("409"); verifyNoInteractions(projects); }
    @Test void outdatedEvidenceIsRejected() { setup(true); indexes.selectDocument(3L).setActiveVersionId(9L); assertThatThrownBy(()->service.draft("r",5,"BEGINNER_PROJECT",7)).hasMessageContaining("409"); }
    @Test void downlistedProjectIsRejected() { setup(true); when(projects.detail(anyString(),anyLong(),anyMap())).thenThrow(new IllegalArgumentException()); assertThatThrownBy(()->service.draft("r",5,"BEGINNER_PROJECT",7)).hasMessageContaining("409"); }
    @Test void platformStringIdsRemainImportableAfterJsonRoundTrip() {
        setup(true);
        when(store.run("r",5)).thenReturn(Map.of("status","SUCCEEDED","session_id",1,"result_json","{\"projects\":[{\"type\":\"BEGINNER_PROJECT\",\"id\":\"7\",\"name\":\"owner/repo\",\"url\":\"https://github.com/owner/repo\",\"importable\":true,\"references\":[{\"documentId\":\"3\",\"versionId\":\"8\"}]}]}"));
        assertThat(service.draft("r",5,"BEGINNER_PROJECT",7)).containsKey("draft");
    }
    void workflowPurpose(String verdict,String capability) {
        setup(true);var state=new LinkedHashMap<String,Object>();CustomerWorkflow.initialize(state,List.of(),"seed");var task=CustomerWorkflow.task(state);
        var requirement=CustomerRequirementService.entry(task,"topic","商品和库存管理",List.of("库存管理"),"SOFT","message","商品和库存管理","MESSAGE");task.put("requirements",List.of(requirement));task.put("requirementVersion",3);
        var match=new LinkedHashMap<String,Object>();match.put("requirementId",requirement.get("id"));match.put("requirementRevision",1);match.put("strength","SOFT");match.put("verdict",verdict);match.put("capabilityScope",capability);match.put("evidenceRefs",List.of(Map.of("documentId",3,"versionId",8,"quote","已实现商品与库存余额管理")));
        var card=new LinkedHashMap<String,Object>();card.put("type","BEGINNER_PROJECT");card.put("id",7);card.put("name","owner/repo");card.put("url","https://github.com/owner/repo");card.put("importable",true);card.put("taskId",task.get("id"));card.put("requirementVersion",3);card.put("requirementMatches",List.of(match));card.put("references",List.of(Map.of("documentId",3,"versionId",8)));
        when(store.run("r",5)).thenReturn(Map.of("status","SUCCEEDED","session_id",1,"result_json",json.write(Map.of("projects",List.of(card)))));
        when(store.session(1,5)).thenReturn(Map.of("state_json",json.write(state)));
        when(projects.detail(eq("BEGINNER_PROJECT"),eq(7L),anyMap())).thenReturn(Map.of("name","owner/repo","url","https://github.com/owner/repo"));
    }
    @Test void historicalImportableFlagCannotBypassUnknownSoftBusinessPurpose(){workflowPurpose("UNKNOWN","UNKNOWN");assertThatThrownBy(()->service.draft("r",5,"BEGINNER_PROJECT",7)).hasMessageContaining("具体用途");verifyNoInteractions(projects);}
    @Test void adaptationIsNotExistingBusinessFunctionEvenWhenSavedAsSatisfied(){workflowPurpose("SATISFIED","ADAPTATION");assertThatThrownBy(()->service.draft("r",5,"BEGINNER_PROJECT",7)).hasMessageContaining("具体用途");}
    @Test void verifiedExistingBusinessPurposeStillAllowsDraft(){workflowPurpose("SATISFIED","EXISTING");assertThat(service.draft("r",5,"BEGINNER_PROJECT",7)).containsKey("draft");}
}
