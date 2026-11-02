package com.orangejd.server.ragb.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in, frozen file corpus; never starts Spring, Docker, a database or a model client. */
@EnabledIfSystemProperty(named = "readme.preview.root", matches = ".+")
class ReadmeKnowledgeFilterCorpusTest {
    @Test void generateFrozenOfflinePreview() throws Exception {
        Path root = Path.of(System.getProperty("readme.preview.root"));
        String split = System.getProperty("readme.preview.split", "development");
        var summary = ReadmeFilterOfflineCli.run(root.resolve("_experiment_staging/readme-filter-v1-20261029/snapshot.json"),
                root.resolve("docs/rag-b/readme-filter-v1/sample-manifest.json"),
                root.resolve("_experiment_staging/readme-filter-k2/"), split);
        assertEquals(switch (split) { case "development" -> 24; case "validation" -> 14; default -> 38; }, summary.get("documents"));
        assertEquals(true, summary.get("sourceMappingVerified"));
        assertEquals(0, summary.get("indexWrites"));
    }
}
