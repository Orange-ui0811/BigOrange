package com.orangejd.server.ragb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.foundation.task.SystemTaskService;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.domain.RagBChunk;
import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.ragb.domain.RagBDocumentVersion;
import com.orangejd.server.ragb.ingest.RagBChunker;
import com.orangejd.server.ragb.ingest.RagBMetadataExtractor;
import com.orangejd.server.ragb.ingest.RagBBusinessMetadataProvider;
import com.orangejd.server.ragb.mapper.RagBMapper;
import com.orangejd.server.ragb.retrieval.RagBVectorRepository;
import com.orangejd.server.ragb.task.RagBIndexTask;
import com.orangejd.server.mapper.KnowledgeDocumentMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagBDocumentServiceTest {
    @Test
    void uploadCreatesPendingVersionAndTraceableChunksBeforeEnqueue() {
        RagBProperties properties = new RagBProperties();
        properties.setEnabled(true);
        properties.setChunkSize(100);
        properties.setChunkOverlap(20);
        RagBMapper mapper = mock(RagBMapper.class);
        SystemTaskService tasks = mock(SystemTaskService.class);
        doAnswer(invocation -> { ((RagBDocument) invocation.getArgument(0)).setId(21L); return 1; })
                .when(mapper).insertDocument(any());
        doAnswer(invocation -> { ((RagBDocumentVersion) invocation.getArgument(0)).setId(34L); return 1; })
                .when(mapper).insertVersion(any());
        when(tasks.enqueue(eq(RagBIndexTask.TASK_TYPE), any(), any(), eq(3))).thenReturn(55L);
        RagBDocumentService service = new RagBDocumentService(properties, new RagBChunker(properties), mapper,
                new ObjectMapper(), tasks, mock(RagBVectorRepository.class), mock(KnowledgeDocumentMapper.class),
                new RagBMetadataExtractor(), mock(RagBBusinessMetadataProvider.class));
        String content = "# 部署\n" + "使用 Docker 启动服务。".repeat(30);

        Map<String, Object> result = service.upload(new MockMultipartFile("file", "README.md", "text/markdown",
                content.getBytes(StandardCharsets.UTF_8)), null);

        ArgumentCaptor<RagBDocument> document = ArgumentCaptor.forClass(RagBDocument.class);
        ArgumentCaptor<RagBDocumentVersion> version = ArgumentCaptor.forClass(RagBDocumentVersion.class);
        ArgumentCaptor<RagBChunk> chunks = ArgumentCaptor.forClass(RagBChunk.class);
        verify(mapper).insertDocument(document.capture());
        verify(mapper).insertVersion(version.capture());
        verify(mapper, org.mockito.Mockito.atLeastOnce()).insertChunk(chunks.capture());
        assertThat(document.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(version.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(version.getValue().getChunkCount()).isEqualTo(chunks.getAllValues().size());
        assertThat(chunks.getAllValues()).allMatch(chunk -> chunk.getStartOffset() < chunk.getEndOffset())
                .allMatch(chunk -> "PENDING".equals(chunk.getIndexStatus()));
        assertThat(chunks.getAllValues().getLast().getEndOffset()).isEqualTo(content.length());
        assertThat(result).containsEntry("status", "PENDING").containsEntry("taskId", 55L);
        verify(tasks).enqueue(eq(RagBIndexTask.TASK_TYPE), eq("document:21:version:34:retry:0"), any(), anyInt());
    }
}
