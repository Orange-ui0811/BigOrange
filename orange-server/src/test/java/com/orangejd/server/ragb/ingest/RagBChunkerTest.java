package com.orangejd.server.ragb.ingest;

import com.orangejd.server.ragb.config.RagBProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagBChunkerTest {
    @Test
    void coversTheCompleteDocumentAndTracksHeadingPath() {
        RagBProperties properties = new RagBProperties();
        properties.setChunkPolicy("CHAR_500_V1");
        properties.setChunkSize(100);
        properties.setChunkOverlap(20);
        RagBChunker chunker = new RagBChunker(properties);
        String content = "# 安装\n" + "准备环境。".repeat(30) + "\n\n## Docker\n" + "启动服务。".repeat(30);

        List<RagBChunker.ChunkDraft> chunks = chunker.split(content);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.getFirst().startOffset()).isZero();
        assertThat(chunks.getLast().endOffset()).isEqualTo(content.length());
        assertThat(chunks).anyMatch(chunk -> "安装 > Docker".equals(chunk.sectionPath()));
        for (int i = 1; i < chunks.size(); i++) {
            assertThat(chunks.get(i).startOffset()).isLessThanOrEqualTo(chunks.get(i - 1).endOffset());
            assertThat(chunks.get(i).startOffset()).isGreaterThan(chunks.get(i - 1).startOffset());
        }
    }

    @Test
    void rejectsInsteadOfSilentlyTruncatingWhenChunkLimitIsReached() {
        RagBProperties properties = new RagBProperties();
        properties.setChunkPolicy("CHAR_500_V1");
        properties.setChunkSize(100);
        properties.setChunkOverlap(10);
        properties.setMaxChunks(2);

        assertThatThrownBy(() -> new RagBChunker(properties).split("内容".repeat(300)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未创建不完整索引");
    }
}
