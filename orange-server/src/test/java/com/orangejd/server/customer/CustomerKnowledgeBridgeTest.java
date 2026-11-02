package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.pojo.entity.KnowledgeDocument;
import com.orangejd.server.mapper.KnowledgeDocumentMapper;
import com.orangejd.server.ragb.mapper.RagBMapper;
import com.orangejd.server.ragb.service.RagBDocumentService;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.foundation.task.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerKnowledgeBridgeTest {
    CustomerProperties config=new CustomerProperties(); RagBProperties rag=new RagBProperties(); SystemTaskService tasks=mock(SystemTaskService.class);
    KnowledgeDocumentMapper legacy=mock(KnowledgeDocumentMapper.class); RagBMapper indexes=mock(RagBMapper.class); RagBDocumentService documents=mock(RagBDocumentService.class);
    CustomerJson json=new CustomerJson(new ObjectMapper()); CustomerKnowledgeBridge bridge=new CustomerKnowledgeBridge(config,rag,tasks,legacy,indexes,documents,json);
    KnowledgeDocument source=new KnowledgeDocument();
    @BeforeEach void setup() { config.setEnabled(true); rag.setEnabled(true); source.setId(42L); source.setSource("MANUAL"); source.setTitle("测试资料"); source.setOriginalContent("original source"); when(legacy.selectById(42L)).thenReturn(source); }
    @Test void insertAndUpdateShareDurableIdempotentTask() {
        bridge.inserted(source); bridge.updated(source);
        var keys=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(tasks,times(2)).enqueue(eq("CUSTOMER_KNOWLEDGE_SYNC"),keys.capture(),eq(Map.of("operation","UPSERT","legacyId",42L)),eq(3));
        assertThat(keys.getAllValues().get(0)).isEqualTo(keys.getAllValues().get(1));
    }
    @Test void deletionExcludesEvidenceBeforeSourceDeleteAndQueuesCleanupAfter() throws Throwable {
        RagBDocument target=new RagBDocument(); target.setId(99L); when(indexes.selectDocumentBySource("MANUAL","legacy-document:42")).thenReturn(target);
        ProceedingJoinPoint operation=mock(ProceedingJoinPoint.class); when(operation.proceed()).thenReturn(1);
        assertThat(bridge.deleted(operation,42L)).isEqualTo(1);
        var order=inOrder(indexes,operation,tasks); order.verify(indexes).selectDocumentBySource("MANUAL","legacy-document:42");
        order.verify(indexes).tombstone(99L); order.verify(indexes).tombstoneChunks(99L); order.verify(operation).proceed(); order.verify(tasks).enqueue(anyString(),anyString(),anyMap(),eq(3));
    }
    @Test void concurrentSourceDeletionTombstonesLateImport() {
        when(legacy.selectById(42L)).thenReturn(source,null); when(documents.importLegacy(42L)).thenReturn(Map.of("documentId",99L));
        SystemTask task=new SystemTask(); task.setPayloadJson(json.write(Map.of("operation","UPSERT","legacyId",42L)));
        assertThat(bridge.execute(task)).isEqualTo(Map.of("status","SOURCE_REMOVED")); verify(indexes).tombstone(99L); verify(indexes).tombstoneChunks(99L);
    }
    @Test void disabledCustomerDoesNotChangeLegacyPipeline() { config.setEnabled(false); bridge.inserted(source); verifyNoInteractions(tasks); }
    @Test void platformStringIdsAreAcceptedInDurablePayload() {
        when(documents.importLegacy(42L)).thenReturn(Map.of("documentId",99L));
        SystemTask task=new SystemTask(); task.setPayloadJson("{\"operation\":\"UPSERT\",\"legacyId\":\"42\"}");
        assertThat(bridge.execute(task)).isEqualTo(Map.of("documentId",99L)); verify(documents).importLegacy(42L);
    }
}
