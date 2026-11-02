package com.orangejd.server.ragb.ingest;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** R4 local planning only: the transport throws if any provider invocation is attempted. */
@EnabledIfSystemProperty(named="readme.r4.root", matches=".+")
class K3R4FrozenPlanTest {
    @Test void exportExactPlansWithoutProviderOrIndexAccess() throws Exception {
        Path root=Path.of(System.getProperty("readme.r4.root"));
        Path stage=root.resolve("_experiment_staging/readme-quality-v2");
        ObjectMapper json=new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        var selection=json.readTree(Files.readString(stage.resolve("selection.json")));
        var runtime=json.readTree(Files.readString(stage.resolve("provider-config.json")));
        Map<String,JsonNode> corpus=new LinkedHashMap<>();
        json.readTree(Files.readString(root.resolve("_experiment_staging/readme-filter-v1-20261029/snapshot.json")))
                .path("documents").forEach(d->corpus.put(d.path("documentId").asText(),d));
        var filter=new ReadmeKnowledgeFilter();
        var limits=new ReadmeModelReview.Limits(0,12000,2048,4,0,0);
        var engine=new ReadmeModelReview(runtime.path("modelIdentity").asText(),limits,stage.resolve("unused-cache"),
                new ReadmeReviewJournal(stage.resolve("unused-ledger.jsonl"),"R4_PLAN_ONLY",true),
                (s,u,o)->{throw new AssertionError("R4 forbids provider calls");});
        List<Map<String,Object>> documents=new ArrayList<>();
        for(JsonNode item:selection.path("documents")) {
            JsonNode doc=corpus.get(item.path("documentId").asText());assertNotNull(doc);
            var source=new ReadmeKnowledgeFilter.Source(doc.path("documentId").asText(),doc.path("versionId").asText(),
                    doc.path("sourceRef").asText(),doc.path("content").asText());
            var preview=filter.preview(source);
            var projection=new ReadmeRecommendationProjection().project(source,preview,List.of());
            ReadmeRecommendationProjection.verify(source,projection.preview(),projection.projection());
            var batches=engine.plan(source,preview);
            for(var batch:batches) {
                assertTrue(batch.targetIds().size()<=4);assertTrue(batch.inputUpperBound()<=12000);
                assertEquals(ReadmeModelReview.systemPrompt(),batch.system());
            }
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("source",source);row.put("sourceSha256",preview.sourceSha256());row.put("blocks",preview.blocks());
            row.put("dependencies",ReadmeKnowledgeFilter.dependencies(preview.blocks()));row.put("batches",batches);
            row.put("ruleOnlyProjection",projection.projection());documents.add(row);
        }
        assertEquals(12,documents.size());assertFalse(Files.exists(stage.resolve("unused-ledger.jsonl")));
        var versions=Map.of("parser",ReadmeKnowledgeFilter.PARSER,"rulePolicy",ReadmeKnowledgeFilter.POLICY,
                "selectionPolicy",ReadmeModelReview.POLICY,"prompt",ReadmeModelReview.PROMPT,"schema",ReadmeModelReview.SCHEMA,
                "projectionPolicy",ReadmeRecommendationProjection.POLICY);
        Files.writeString(stage.resolve("all-plans.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(
                Map.of("documents",documents,"versions",versions,"providerCalls",0,"indexWrites",0))+"\n");
    }
}
