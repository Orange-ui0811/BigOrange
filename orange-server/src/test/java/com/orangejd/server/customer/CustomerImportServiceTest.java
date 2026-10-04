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
}
