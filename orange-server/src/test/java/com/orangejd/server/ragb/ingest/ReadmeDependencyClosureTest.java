package com.orangejd.server.ragb.ingest;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;
import static com.orangejd.server.ragb.ingest.ReadmeModelReview.*;
import static org.junit.jupiter.api.Assertions.*;

/** Frozen source regressions with simulated choices, never a classification quality measurement. */
class ReadmeDependencyClosureTest {
    @TempDir Path temp;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ReadmeKnowledgeFilter filter = new ReadmeKnowledgeFilter();

    private Source frozen(String id) throws Exception {
        try (var stream = getClass().getResourceAsStream("/readme-filter/dependency-v2/sources.json")) {
            assertNotNull(stream);
            for (JsonNode node : JSON.readTree(stream).path("documents")) {
                if (!node.path("documentId").asText().equals(id)) continue;
                var source = new Source(id, node.path("versionId").asText(), node.path("sourceRef").asText(), node.path("content").asText());
                assertEquals(node.path("sha256").asText(), sha256(source.text()));
                return source;
            }
        }
        throw new AssertionError("Missing frozen source");
    }
    private Block at(Preview preview, int line) {
        return preview.blocks().stream().filter(b -> b.startLine() == line).findFirst().orElseThrow();
    }
    private Block text(Preview preview, String needle) {
        return preview.blocks().stream().filter(b -> b.text().contains(needle)).findFirst().orElseThrow();
    }
    private ReadmeModelReview engine(int maxBlocks, Transport transport) {
        return new ReadmeModelReview("SIMULATED_DEPENDENCY_TEST", new Limits(1, 12000, 2048, maxBlocks, 0, 50000),
                temp.resolve("cache"), new ReadmeReviewJournal(temp.resolve("simulated.jsonl"), "test", true), transport);
    }
    private Reply choices(String user, String unresolved) throws Exception {
        var selections = JSON.createArrayNode();
        for (JsonNode target : JSON.readTree(user).path("targets")) {
            boolean review = unresolved != null && target.path("raw").asText().contains(unresolved);
            var row = selections.addObject().put("blockId", target.path("blockId").asText()).put("decision", review ? "REVIEW" : "KEEP")
                    .put("kind", review ? "OTHER" : "USAGE").put("scope", review ? "UNKNOWN" : "CORE")
                    .put("reason", review ? "UNCERTAIN" : "KNOWLEDGE").put("applicability", review ? "UNKNOWN" : "IN_SCOPE");
            row.putArray("evidenceBlockIds").add(target.path("blockId").asText());
        }
        return new Reply(JSON.createObjectNode().put("schemaVersion", SCHEMA).set("selections", selections).toString(), "stop", Map.of());
    }
    private boolean primary(Preview p, String id) {
        return p.knowledgeCandidates().stream().anyMatch(packet -> packet.primaryBlockIds().contains(id));
    }
    private Result reviewContaining(Source source, ReadmeModelReview engine, String code) throws Exception {
        var initial = filter.preview(source);
        String id = text(initial, code).blockId();
        Batch batch = engine.plan(source, initial).stream().filter(b -> b.targetIds().contains(id)).findFirst().orElseThrow();
        return engine.review(source, initial, Set.of(batch.cacheKey()));
    }

    @Test void q14626InstallDoesNotDependOnSocialBadge() throws Exception {
        var source = frozen("46"); var p = filter.preview(source);
        assertEquals(Decision.KEEP, at(p, 26).decision());
        assertTrue(primary(p, at(p, 26).blockId()));
        var packet = p.knowledgeCandidates().stream().filter(x -> x.primaryBlockIds().contains(at(p, 26).blockId())).findFirst().orElseThrow();
        assertTrue(packet.primaryBlockIds().contains(at(p, 24).blockId()) || packet.contextBlockIds().contains(at(p, 24).blockId()));
        assertFalse(packet.contextBlockIds().contains(at(p, 19).blockId()));
        assertEquals(Decision.REVIEW, at(p, 19).decision(), "Unrelated image stays quarantined");
        verify(source, p);
    }
    @Test void q152336PreviewAndExplanationShareOneBatch() throws Exception {
        var source = frozen("523"); var p = filter.preview(source);
        var engine = engine(4, (s,u,o) -> choices(u, null));
        Set<String> required = new HashSet<>();
        for (int line : List.of(26, 30, 34, 36)) required.add(at(p, line).blockId());
        Batch batch = engine.plan(source, p).stream().filter(b -> b.targetIds().contains(at(p,36).blockId())).findFirst().orElseThrow();
        assertEquals(required, new HashSet<>(batch.targetIds()));
        var result = engine.review(source, p, Set.of(batch.cacheKey()));
        for (String id : required) assertTrue(primary(result.preview(), id), id);
        assertEquals(1, result.attempts().size()); verify(source, result.preview());
    }
    @Test void q152348RestoreLeadInAndBothCommandsAreRetainedTogether() throws Exception {
        var source = frozen("523"); var p = filter.preview(source);
        var engine = engine(4, (s,u,o) -> choices(u, null));
        var result = reviewContaining(source, engine, "rake integrate");
        var ids = List.of(42, 44, 48, 50).stream().map(line -> at(p, line).blockId()).toList();
        assertEquals(new HashSet<>(ids), new HashSet<>(result.batches().getFirst().targetIds()));
        assertTrue(result.preview().knowledgeCandidates().stream().anyMatch(packet -> packet.primaryBlockIds().containsAll(ids)));
        verify(source, result.preview());
    }
    @Test void reassemblingUnchangedCompleteExampleIsIdempotent() {
        var source = new Source("id", "v", "o/r", "# Tool\n某项尚未判定的操作：\n\n```sh\nrun task\n```\n");
        var p = filter.preview(source);
        assertEquals(p, filter.reassemble(source, p, p.blocks()));
        assertNotEquals("INCOMPLETE_EXAMPLE_CONTEXT", text(p, "某项").reason());
    }
    @Test void unresolvedRestoreStepPreventsPartialExamplePublication() throws Exception {
        var source = frozen("523");
        var result = reviewContaining(source, engine(4, (s,u,o) -> choices(u, "rake integrate")), "rake integrate");
        for (int line : List.of(44, 48, 50)) {
            Block b = at(result.preview(), line);
            assertEquals(Decision.REVIEW, b.decision()); assertFalse(primary(result.preview(), b.blockId()));
        }
    }
    @Test void oversizedDependencyUnitIsDeferredWithoutPartialRequests() throws Exception {
        var source = frozen("523"); var p = filter.preview(source);
        var engine = engine(3, (s,u,o) -> { fail("A four-target unit cannot be split to fit three"); return null; });
        var batches = engine.plan(source, p);
        Set<String> protectedIds = new HashSet<>();
        for (int line : List.of(26, 30, 34, 36, 42, 44, 48, 50)) protectedIds.add(at(p, line).blockId());
        assertTrue(batches.stream().flatMap(b -> b.targetIds().stream()).noneMatch(protectedIds::contains));
    }
    @Test void brokenExampleCannotBeRescuedByModelOrUnchangedReassembly() throws Exception {
        var source = new Source("broken", "v", "o/r", "# Tool\n使用说明：\n\n```js\nimport 'unfinished\n```\n");
        var p = filter.preview(source);
        var engine = engine(4, (s,u,o) -> { fail("Hard review must not be sent"); return null; });
        var result = engine.review(source, p);
        assertTrue(result.batches().isEmpty()); assertEquals(p.blocks(), result.preview().blocks());
        assertEquals("INCOMPLETE_EXAMPLE_CONTEXT", text(p, "使用说明").reason());
        assertTrue(result.preview().knowledgeCandidates().isEmpty());
    }
    @Test
    @EnabledIfSystemProperty(named="readme.dependency.root", matches=".+")
    void frozenCorpusKeepsMappingsClosedPacketsAndIdempotentAssembly() throws Exception {
        Path root = Path.of(System.getProperty("readme.dependency.root"));
        Path snapshotPath = root.resolve("_experiment_staging/readme-filter-v1-20261029/snapshot.json");
        String raw = Files.readString(snapshotPath);
        assertEquals("1edacde294596505856e85587a3ec2f8f152f54eef7b0aa15fbced3c6ce7e0c3", sha256(raw));
        int count = 0, packets = 0;
        for (JsonNode doc : JSON.readTree(raw).path("documents")) {
            var source = new Source(doc.path("documentId").asText(), doc.path("versionId").asText(),
                    doc.path("sourceRef").asText(), doc.path("content").asText());
            var p = filter.preview(source); verify(source, p);
            assertEquals(p, filter.reassemble(source, p, p.blocks()), source.sourceRef());
            var dependencies = ReadmeKnowledgeFilter.dependencies(p.blocks());
            for (var group : List.of(p.knowledgeCandidates(), p.pointerCandidates())) for (Packet packet : group) {
                Set<String> present = new HashSet<>(packet.primaryBlockIds()); present.addAll(packet.contextBlockIds());
                for (String id : present) assertTrue(present.containsAll(dependencies.get(id)), source.sourceRef() + " missing dependency");
                packets++;
            }
            count++;
        }
        assertEquals(303, count);
        Path output = Path.of(System.getProperty("readme.dependency.output",root.resolve("_experiment_staging/readme-dependency-v2").toString()));
        Files.createDirectories(output);
        var summary = ReadmeFilterOfflineCli.run(snapshotPath, root.resolve("docs/rag-b/readme-filter-v1/sample-manifest.json"), output.resolve("regression"), "all");
        assertEquals(38, summary.get("documents"));
        Files.writeString(output.resolve("corpus-check.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(
                Map.of("documents", count, "packets", packets, "sourceMappingErrors", 0, "missingDependencies", 0,
                        "idempotenceErrors", 0, "providerCalls", 0, "indexWrites", 0, "rulePolicy", ReadmeKnowledgeFilter.POLICY)));
    }
}
