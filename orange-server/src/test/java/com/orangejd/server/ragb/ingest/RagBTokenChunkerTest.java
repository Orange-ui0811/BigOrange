package com.orangejd.server.ragb.ingest;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RagBTokenChunkerTest {
    @Test void budgetsRealM3TokensAndPreservesUnicodeAndCoverage() {
        String text="# Root\n\n" + "A multilingual 技术 paragraph with emoji 😀。\n".repeat(150)
                + "\n## Deploy\n\n```java\n# not a heading\n" + "println(\"你好\");\n".repeat(150) + "```\n";
        RagBTokenChunker chunker=new RagBTokenChunker();
        assertThat(chunker.tokens(text)).isGreaterThan(768);
        var chunks=chunker.split(text,4096); int covered=0;
        for(var c:chunks) {
            assertThat(c.startOffset()).isLessThanOrEqualTo(covered);
            assertThat(c.content()).isEqualTo(text.substring(c.startOffset(),c.endOffset()));
            assertThat(chunker.tokens(c.content())).isLessThanOrEqualTo(768);
            assertThat(c.sectionPath()).doesNotContain("not a heading");
            covered=Math.max(covered,c.endOffset());
        }
        assertThat(covered).isEqualTo(text.length());
        assertThat(chunks).anyMatch(c->"Root > Deploy".equals(c.sectionPath()));
    }
    @Test void recognizesSetextAndRefusesIncompleteCoverage() {
        RagBTokenChunker chunker=new RagBTokenChunker();
        var chunks=chunker.split("Title\n=====\n\nDeploy\n------\n说明",4096);
        assertThat(chunks).anyMatch(c->"Title > Deploy".equals(c.sectionPath()));
        assertThatThrownBy(()->chunker.split("words 技术 ".repeat(5000),1)).hasMessageContaining("未创建不完整索引");
    }
}
