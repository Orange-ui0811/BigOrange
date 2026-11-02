package com.orangejd.server.ragb.service;

import com.orangejd.server.foundation.task.SystemTaskService;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.domain.RagBChunk;
import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.ragb.domain.RagBIndexGeneration;
import com.orangejd.server.ragb.mapper.RagBMapper;
import com.orangejd.server.ragb.retrieval.RagBIndexRouter;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagBGenerationServiceTest {
    @Test
    void publishesPreparedStructuralVersionsOnlyAfterVectorValidation() {
        Fixture fixture = fixture();
        RagBDocumentService documents = mock(RagBDocumentService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(fixture.service, "documents", documents);
        fixture.generation.setChunkPolicy("STRUCTURE_M3_512_768_64_V1");
        when(documents.prepareGenerationVersion(fixture.document, fixture.generation)).thenReturn(40L);
        when(fixture.mapper.selectChunks(40L)).thenReturn(List.of(fixture.chunk));
        when(fixture.indexes.index(fixture.document, fixture.generation, List.of(fixture.chunk)))
                .thenReturn(Map.of(31L, "new:31"));
        when(fixture.indexes.adapterType(fixture.generation)).thenReturn("HNSW_HYBRID");
        when(fixture.mapper.countGenerationChunks(5L)).thenReturn(1);
        when(fixture.mapper.markGenerationReady(5L, 1, 1)).thenReturn(1);
        when(fixture.mapper.activateGeneration(5L)).thenReturn(1);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked") Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(fixture.transactions).executeWithoutResult(any());

        fixture.service.rebuild(5L);

        var order = org.mockito.Mockito.inOrder(fixture.mapper);
        order.verify(fixture.mapper).countGenerationChunks(5L);
        order.verify(fixture.mapper).markGenerationReady(5L, 1, 1);
        order.verify(fixture.mapper).retireActiveGeneration();
        order.verify(fixture.mapper).activateGeneration(5L);
        order.verify(fixture.mapper).publishGenerationChunkKeys(5L);
        order.verify(fixture.mapper).publishGenerationVersions(5L);
    }

    @Test
    void neverPublishesPreparedVersionsWhenStructuralIndexIsIncomplete() {
        Fixture fixture = fixture();
        RagBDocumentService documents = mock(RagBDocumentService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(fixture.service, "documents", documents);
        fixture.generation.setChunkPolicy("STRUCTURE_M3_512_768_64_V1");
        when(documents.prepareGenerationVersion(fixture.document, fixture.generation)).thenReturn(40L);
        when(fixture.mapper.selectChunks(40L)).thenReturn(List.of(fixture.chunk));
        when(fixture.indexes.index(fixture.document, fixture.generation, List.of(fixture.chunk)))
                .thenReturn(Map.of(31L, "new:31"));
        when(fixture.mapper.countGenerationChunks(5L)).thenReturn(0);

        assertThatThrownBy(() -> fixture.service.rebuild(5L)).hasMessageContaining("新索引校验失败");

        verify(fixture.mapper, never()).retireActiveGeneration();
        verify(fixture.mapper, never()).publishGenerationVersions(5L);
        verify(fixture.transactions, never()).executeWithoutResult(any());
    }

    @Test
    void switchesOnlyAfterEveryChunkIsIndexedAndSnapshotIsStable() {
        Fixture fixture = fixture();
        when(fixture.indexes.index(fixture.document, fixture.generation, List.of(fixture.chunk)))
                .thenReturn(Map.of(31L, "orange:rag:b:g:new:chunk:31"));
        when(fixture.indexes.adapterType(fixture.generation)).thenReturn("EXACT");
        when(fixture.mapper.countGenerationChunks(5L)).thenReturn(1);
        when(fixture.mapper.markGenerationReady(5L, 1, 1)).thenReturn(1);
        when(fixture.mapper.activateGeneration(5L)).thenReturn(1);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked") Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(fixture.transactions).executeWithoutResult(any());

        Map<String, Object> result = fixture.service.rebuild(5L);

        assertThat(result).containsEntry("status", "ACTIVE").containsEntry("chunks", 1);
        verify(fixture.mapper).retireActiveGeneration();
        verify(fixture.mapper).activateGeneration(5L);
        verify(fixture.mapper).publishGenerationChunkKeys(5L);
    }

    @Test
    void keepsCurrentGenerationWhenNewVectorBuildFails() {
        Fixture fixture = fixture();
        when(fixture.indexes.index(fixture.document, fixture.generation, List.of(fixture.chunk)))
                .thenThrow(new IllegalStateException("embedding unavailable"));

        assertThatThrownBy(() -> fixture.service.rebuild(5L)).hasMessageContaining("embedding unavailable");

        verify(fixture.mapper).markGenerationFailed(5L, "embedding unavailable");
        verify(fixture.mapper, never()).retireActiveGeneration();
        verify(fixture.mapper, never()).activateGeneration(5L);
    }

    private Fixture fixture() {
        RagBProperties properties = new RagBProperties();
        properties.setEnabled(true);
        RagBMapper mapper = mock(RagBMapper.class);
        RagBIndexRouter indexes = mock(RagBIndexRouter.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        RagBIndexGeneration generation = new RagBIndexGeneration();
        generation.setId(5L);
        generation.setGenerationKey("new");
        generation.setRedisPrefix("orange:rag:b:g:new:");
        generation.setActive(false);
        generation.setStatus("BUILDING");
        RagBDocument document = new RagBDocument();
        document.setId(20L);
        document.setActiveVersionId(30L);
        document.setStatus("READY");
        RagBChunk chunk = new RagBChunk();
        chunk.setId(31L);
        chunk.setVersionId(30L);
        when(mapper.selectGeneration(5L)).thenReturn(generation);
        when(mapper.selectReadyDocuments()).thenReturn(List.of(document), List.of(document));
        when(mapper.selectChunks(30L)).thenReturn(List.of(chunk));
        RagBGenerationService service = new RagBGenerationService(properties, mapper, indexes,
                mock(SystemTaskService.class), transactions);
        return new Fixture(service, mapper, indexes, transactions, generation, document, chunk);
    }

    private record Fixture(RagBGenerationService service, RagBMapper mapper, RagBIndexRouter indexes,
                           TransactionTemplate transactions, RagBIndexGeneration generation,
                           RagBDocument document, RagBChunk chunk) {}
}
