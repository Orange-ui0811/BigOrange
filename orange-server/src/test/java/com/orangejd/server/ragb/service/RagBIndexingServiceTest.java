package com.orangejd.server.ragb.service;

import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.ragb.domain.RagBDocumentVersion;
import com.orangejd.server.ragb.mapper.RagBMapper;
import com.orangejd.server.ragb.retrieval.RagBIndexRouter;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagBIndexingServiceTest {
    @Test
    void acceptsTwoAffectedRowsFromMySqlMultiTableProcessingUpdate() {
        RagBProperties properties = new RagBProperties();
        properties.setEnabled(true);
        RagBMapper mapper = mock(RagBMapper.class);
        RagBDocument document = new RagBDocument();
        document.setId(1L);
        document.setStatus("PENDING");
        RagBDocumentVersion version = new RagBDocumentVersion();
        version.setId(2L);
        version.setDocumentId(1L);
        version.setStatus("PENDING");
        when(mapper.selectDocument(1L)).thenReturn(document);
        when(mapper.selectVersion(2L)).thenReturn(version);
        when(mapper.markProcessing(1L, 2L)).thenReturn(2);
        RagBIndexingService service = new RagBIndexingService(properties, mapper,
                mock(RagBIndexRouter.class), mock(TransactionTemplate.class));

        assertThatThrownBy(() -> service.index(1L, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("没有可用的 B 版索引代际");

        verify(mapper).markFailed(1L, 2L, "没有可用的 B 版索引代际");
    }
}
