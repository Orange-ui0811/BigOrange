package com.orangejd.server.ragb.ingest;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;
import static org.junit.jupiter.api.Assertions.*;

/** Consumes sealed results, not response replays. Never writes to any earlier K3 stage. */
class ReadmeKnowledgeChunkPreviewTest {
    @Test @EnabledIfSystemProperty(named="readme.chunk.preview.root", matches=".+")
    void previewFiveSealedRepositoriesWithoutProviderOrIndexAccess() throws Exception {
        Path root = Path.of(System.getProperty("readme.chunk.preview.root"));
        Path pilot = root.resolve("docs/rag-b/readme-filter-v1/k3/quality-v2/v5-pilot-v3");
        Path output = root.resolve("docs/rag-b/readme-filter-v1/chunk-v2/previews");
        Files.createDirectories(output); ObjectMapper json = new ObjectMapper();
        var sources = json.readTree(Files.readString(pilot.resolve("sources.json"))).path("documents");
        List<Map<String,Object>> summaries = new ArrayList<>();
        for (var row : sources) {
            Source source = json.treeToValue(row.path("source"), Source.class);
            var saved = json.readTree(Files.readString(pilot.resolve("results/diagnostic/" + source.documentId() + ".json"))).path("result");
            var result = json.treeToValue(saved, ReadmeModelReview.Result.class);
            var chunker = new ReadmeKnowledgeChunker();
            var preview = chunker.preview(source, result.preview(), result.recommendation(),
                    new ReadmeKnowledgeChunker.Identity(null, "OFFLINE_README_CHUNK_V2_20261031"));
            assertEquals(preview, chunker.preview(source, result.preview(), result.recommendation(), preview.identity()));
            for (var hit : preview.chunks()) {
                var evidence = chunker.evidence(preview, hit.chunkId(), 8192);
                assertTrue(Set.of("READY", "DEFERRED_EVIDENCE_BUDGET").contains(evidence.status()));
            }
            if (source.documentId().equals("244")) {
                String lead = result.preview().blocks().stream().filter(b -> b.startLine() == 155).findFirst().orElseThrow().blockId();
                String child = result.preview().blocks().stream().filter(b -> b.startLine() == 157).findFirst().orElseThrow().blockId();
                assertTrue(preview.units().stream().anyMatch(u -> u.primaryBlockIds().contains(lead) && u.status().equals("DEFERRED")
                        && u.requiredBlockIds().contains(child)));
                assertTrue(preview.chunks().stream().flatMap(c -> c.sourceSpans().stream()).noneMatch(s -> s.blockId().equals(lead) || s.blockId().equals(child)));
                String build = result.preview().blocks().stream().filter(b -> b.startLine() == 120).findFirst().orElseThrow().blockId();
                assertTrue(preview.chunks().stream().flatMap(c -> c.sourceSpans().stream()).anyMatch(s -> s.blockId().equals(build)),
                        "Normal build must survive an unresolved, separate troubleshooting branch");
            }
            Files.writeString(output.resolve(source.documentId() + ".json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(preview) + "\n");
            Set<String> candidateBlocks = new HashSet<>(); result.recommendation().knowledgeCandidates().forEach(c -> candidateBlocks.addAll(c.packet().primaryBlockIds()));
            Set<String> readyBlocks = new HashSet<>(); preview.units().stream().filter(u -> u.status().equals("READY")).forEach(u -> readyBlocks.addAll(u.primaryBlockIds()));
            List<Map<String,Object>> deferred = preview.units().stream().filter(u -> u.status().equals("DEFERRED"))
                    .map(u -> Map.<String,Object>of("unitId",u.unitId(),"primaryBlockIds",u.primaryBlockIds(),"reasons",u.reasons())).toList();
            Set<String> notReady = new HashSet<>(candidateBlocks); notReady.removeAll(readyBlocks);
            Map<String,Object> summary = new LinkedHashMap<>(); summary.put("documentId",source.documentId()); summary.put("repository",source.sourceRef());
            summary.put("candidatePrimaryBlocks",candidateBlocks.size()); summary.put("readyPrimaryBlocks",readyBlocks.size());
            summary.put("notReadyPrimaryBlockIds",notReady.stream().sorted().toList()); summary.put("units",preview.units().size());
            summary.put("readyUnits",preview.units().stream().filter(u -> u.status().equals("READY")).count()); summary.put("deferredUnits",deferred);
            summary.put("chunks",preview.chunks().size()); summary.put("navigation",preview.navigation().size());
            summary.put("maxEmbeddingTokens",preview.chunks().stream().mapToInt(ReadmeKnowledgeChunker.Chunk::tokens).max().orElse(0));
            summaries.add(summary);
        }
        assertEquals(5,summaries.size());
        Files.writeString(output.getParent().resolve("preview-summary.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "repositories",summaries,"providerCalls",0,"embeddingCalls",0,"indexWrites",0,"productionActivated",false,
                "assessment","Deterministic chunk integrity on five tuned repositories, not independent semantic quality or retrieval evaluation")) + "\n");
    }
}
