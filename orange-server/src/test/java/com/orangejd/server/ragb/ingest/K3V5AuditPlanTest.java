package com.orangejd.server.ragb.ingest;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;
import static org.junit.jupiter.api.Assertions.*;

/** Introspect the unchanged production planner; no provider, application bootstrap or index access. */
@EnabledIfSystemProperty(named="readme.v5.audit.root",matches=".+")
class K3V5AuditPlanTest {
    @Test @SuppressWarnings("unchecked")
    void exportActualPlansUnitsAndGuardEdgesWithoutCallingTransport()throws Exception{
        Path root=Path.of(System.getProperty("readme.v5.audit.root"));
        Path quality=root.resolve("docs/rag-b/readme-filter-v1/k3/quality-v2");
        Path stage=root.resolve("_experiment_staging/readme-v5-audit-v1");Files.createDirectories(stage);
        var json=new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        String identity=json.readTree(Files.readString(quality.resolve("r4/provider-config.json"))).path("modelIdentity").asText();
        var sourceRows=json.readTree(Files.readString(quality.resolve("r4/sources.json"))).path("documents");
        var unitsMethod=ReadmeModelReview.class.getDeclaredMethod("reviewUnits",Preview.class);unitsMethod.setAccessible(true);
        var batchMethod=ReadmeModelReview.class.getDeclaredMethod("batch",Preview.class,List.class);batchMethod.setAccessible(true);
        List<Map<String,Object>> documents=new ArrayList<>();int batchCount=0;
        for(var row:sourceRows){
            Source source=json.treeToValue(row.path("source"),Source.class);var preview=new ReadmeKnowledgeFilter().preview(source);
            assertEquals(row.path("sha256").asText(),preview.sourceSha256());
            var metadata=ReadmeRecommendationProjection.ruleAssessments(preview);
            var ruleOnly=new ReadmeRecommendationProjection().project(source,preview,List.of());
            ReadmeRecommendationProjection.verify(source,ruleOnly.preview(),ruleOnly.projection());
            List<Block> guards=preview.blocks().stream().filter(ReadmeSourceSemantics::condition).toList();
            Map<String,List<String>> applied=new LinkedHashMap<>();
            for(var b:preview.blocks())applied.put(b.blockId(),guards.stream().filter(g->ReadmeSourceSemantics.applies(g,b)).map(Block::blockId).toList());
            List<Map<String,Object>> variants=new ArrayList<>();
            for(int maxBlocks:List.of(4,8)){
                var limits=new ReadmeModelReview.Limits(0,12000,2048,maxBlocks,0,0);
                var engine=new ReadmeModelReview(identity,limits,stage.resolve("unused-cache"),
                        new ReadmeReviewJournal(stage.resolve("unused-ledger.jsonl"),"V5_AUDIT_ONLY",true),
                        (s,u,o)->{throw new AssertionError("Audit must not invoke any provider");});
                var units=(List<List<Block>>)unitsMethod.invoke(engine,preview);
                var batches=engine.plan(source,preview);List<Map<String,Object>> exportedUnits=new ArrayList<>();
                List<String> expected=new ArrayList<>();
                for(var unit:units){
                    var batch=(ReadmeModelReview.Batch)batchMethod.invoke(engine,preview,unit);
                    boolean fits=unit.size()<=maxBlocks&&batch.inputUpperBound()<=12000;
                    if(fits)expected.addAll(batch.targetIds());
                    exportedUnits.add(Map.of("targetIds",batch.targetIds(),"contextIds",batch.contextIds(),"inputUpperBound",batch.inputUpperBound(),
                            "fits",fits,"reason",unit.size()>maxBlocks?"UNIT_TARGET_LIMIT":batch.inputUpperBound()>12000?"UNIT_INPUT_LIMIT":"PLANNED"));
                }
                assertEquals(expected,batches.stream().flatMap(b->b.targetIds().stream()).toList(),"Public planner and unit audit must agree exactly");
                for(var b:batches){
                    assertTrue(b.targetIds().size()<=maxBlocks);assertTrue(b.inputUpperBound()<=12000);
                    assertEquals(b.system().getBytes(StandardCharsets.UTF_8).length+b.user().getBytes(StandardCharsets.UTF_8).length+256,b.inputUpperBound());
                    var data=json.readTree(b.user());assertEquals(source.documentId(),data.path("source").path("documentId").asText());
                }
                batchCount+=batches.size();
                variants.add(Map.of("name","max"+maxBlocks,"maxBlocks",maxBlocks,"maxInputUpperBound",12000,"outputTokens",2048,
                        "batches",batches,"units",exportedUnits));
            }
            Map<String,Object> doc=new LinkedHashMap<>();doc.put("source",source);doc.put("sourceSha256",preview.sourceSha256());
            doc.put("preview",preview);doc.put("ruleAssessments",metadata);doc.put("ruleProjection",ruleOnly.projection());
            doc.put("structuralDependencies",ReadmeKnowledgeFilter.dependencies(preview.blocks()));doc.put("appliedGuardEdges",applied);
            doc.put("needsModel",preview.blocks().stream().filter(b->ReadmeRecommendationProjection.needsModel(b,metadata)).map(Block::blockId).toList());
            doc.put("softEligible",preview.blocks().stream().filter(ReadmeModelReview::eligible).map(Block::blockId).toList());
            doc.put("variants",variants);documents.add(doc);
        }
        assertEquals(12,documents.size());assertFalse(Files.exists(stage.resolve("unused-ledger.jsonl")));
        Map<String,Object> versions=Map.of("parser",ReadmeKnowledgeFilter.PARSER,"rulePolicy",ReadmeKnowledgeFilter.POLICY,
                "selectionPolicy",ReadmeModelReview.POLICY,"prompt",ReadmeModelReview.PROMPT,"schema",ReadmeModelReview.SCHEMA,
                "projectionPolicy",ReadmeRecommendationProjection.POLICY);
        Files.writeString(stage.resolve("all-plans.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "documents",documents,"versions",versions,"modelIdentity",identity,"batchCountAcrossVariants",batchCount,"providerCalls",0,"indexWrites",0))+"\n");
    }
}
