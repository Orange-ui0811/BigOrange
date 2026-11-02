package com.orangejd.server.ragb.ingest;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;
import static com.orangejd.server.ragb.ingest.ReadmeModelReview.*;

/** Fresh outputs only; never overwrites sealed R3/R5/audit artifacts. */
@EnabledIfSystemProperty(named="readme.v5.admission.root",matches=".+")
class K3V5AdmissionEvidenceTest {
    private static final ObjectMapper JSON=new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path root=Path.of(System.getProperty("readme.v5.admission.root"));
    private final Path quality=root.resolve("docs/rag-b/readme-filter-v1/k3/quality-v2");
    private final Path stage=root.resolve("_experiment_staging/readme-v5-admission-repair-v2");
    private void write(String name,Object value)throws Exception{Files.createDirectories(stage);Files.writeString(stage.resolve(name),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value)+"\n");}
    private Map<String,String> versions(){return Map.of("parser",ReadmeKnowledgeFilter.PARSER,"rulePolicy",ReadmeKnowledgeFilter.POLICY,
            "selectionPolicy",ReadmeModelReview.POLICY,"prompt",ReadmeModelReview.PROMPT,"schema",ReadmeModelReview.SCHEMA,"projectionPolicy",ReadmeRecommendationProjection.POLICY);}

    @Test @SuppressWarnings("unchecked")
    void exportFreshPlansAndReplayOnlyAlreadyAcceptedOldSelections()throws Exception{
        var unitMethod=ReadmeModelReview.class.getDeclaredMethod("reviewUnits",Preview.class);unitMethod.setAccessible(true);
        var batchMethod=ReadmeModelReview.class.getDeclaredMethod("batch",Preview.class,List.class);batchMethod.setAccessible(true);
        String identity=JSON.readTree(Files.readString(quality.resolve("r4/provider-config.json"))).path("modelIdentity").asText();
        List<Map<String,Object>> documents=new ArrayList<>(),replayed=new ArrayList<>();int oldCount=0,used=0;List<String> superseded=new ArrayList<>();
        for(var row:JSON.readTree(Files.readString(quality.resolve("r4/sources.json"))).path("documents")){
            Source source=JSON.treeToValue(row.path("source"),Source.class);var p=new ReadmeKnowledgeFilter().preview(source);
            assertEquals(row.path("sha256").asText(),p.sourceSha256());
            var rules=ReadmeRecommendationProjection.ruleAssessments(p);
            Map<String,Block> blocks=new LinkedHashMap<>();p.blocks().forEach(b->blocks.put(b.blockId(),b));
            List<Map<String,Object>> variants=new ArrayList<>();
            for(int size:List.of(4,8)){
                var engine=new ReadmeModelReview(identity,new Limits(0,12000,2048,size,0,0),stage.resolve("unused-cache"),
                        new ReadmeReviewJournal(stage.resolve("unused-ledger.jsonl"),"V16_PLAN_ONLY",true),
                        (s,u,o)->{throw new AssertionError("No network transport allowed");});
                var units=(List<List<Block>>)unitMethod.invoke(engine,p);List<Map<String,Object>> exported=new ArrayList<>();List<String> expected=new ArrayList<>();
                for(var unit:units){var b=(Batch)batchMethod.invoke(engine,p,unit);boolean fits=unit.size()<=size&&b.inputUpperBound()<=12000;
                    if(fits)expected.addAll(b.targetIds());
                    exported.add(Map.of("targetIds",b.targetIds(),"contextIds",b.contextIds(),"inputUpperBound",b.inputUpperBound(),"fits",fits,
                            "reason",unit.size()>size?"UNIT_TARGET_LIMIT":b.inputUpperBound()>12000?"UNIT_INPUT_LIMIT":"PLANNED"));}
                var batches=engine.plan(source,p);assertEquals(expected,batches.stream().flatMap(b->b.targetIds().stream()).toList());
                for(var batch:batches){assertTrue(batch.targetIds().size()<=size);assertTrue(batch.inputUpperBound()<=12000);}
                variants.add(Map.of("name","max"+size,"maxBlocks",size,"maxInputUpperBound",12000,"outputTokens",2048,"batches",batches,"units",exported));
            }
            Map<String,List<String>> guards=new LinkedHashMap<>();var conditions=p.blocks().stream().filter(ReadmeSourceSemantics::condition).toList();
            for(var b:p.blocks())guards.put(b.blockId(),conditions.stream().filter(g->ReadmeSourceSemantics.applies(g,b)).map(Block::blockId).toList());
            Map<String,Object> planned=new LinkedHashMap<>();planned.put("source",source);planned.put("sourceSha256",p.sourceSha256());planned.put("preview",p);
            planned.put("ruleAssessments",rules);planned.put("variants",variants);planned.put("structuralDependencies",ReadmeKnowledgeFilter.dependencies(p.blocks()));
            planned.put("appliedGuardEdges",guards);planned.put("needsModel",p.blocks().stream().filter(b->ReadmeRecommendationProjection.needsModel(b,rules)).map(Block::blockId).toList());
            planned.put("softEligible",p.blocks().stream().filter(ReadmeModelReview::eligible).map(Block::blockId).toList());documents.add(planned);
            List<Selection> choices=new ArrayList<>();
            var old=JSON.readTree(Files.readString(quality.resolve("r5/results/primary/"+source.documentId()+".json"))).path("result");
            for(var item:old.path("selections")){Selection s=JSON.treeToValue(item,Selection.class);oldCount++;
                assertTrue(blocks.containsKey(s.blockId()));assertTrue(s.evidenceBlockIds().stream().allMatch(blocks::containsKey));
                if(blocks.get(s.blockId()).decision()!=Decision.REVIEW&&s.decision()!=blocks.get(s.blockId()).decision()){superseded.add(s.blockId());continue;}
                choices.add(s);}
            used+=choices.size();var out=ReadmeModelReview.assembleSelections(source,p,choices,"R5_ACCEPTED_SELECTIONS_REPLAY_ONLY","V16_OFFLINE_REPLAY");
            ReadmeRecommendationProjection.verify(source,out.preview(),out.projection());
            replayed.add(Map.of("documentId",source.documentId(),"result",Map.of("preview",out.preview(),"recommendation",out.projection(),"selections",choices)));
        }
        assertEquals(12,documents.size());assertEquals(119,oldCount);assertFalse(Files.exists(stage.resolve("unused-ledger.jsonl")));
        write("all-plans.json",Map.of("documents",documents,"versions",versions(),"modelIdentity",identity,"planningPolicy","DIRECTED_SCC_PREREQUISITES_FIRST","providerCalls",0,"indexWrites",0));
        write("replay-results.json",Map.of("documents",replayed,"versions",versions(),"oldAcceptedSelections",oldCount,"reusedSelections",used,
                "supersededByFixedRule",superseded,"newPromptExecuted",false,"providerCalls",0,"indexWrites",0));
    }

    @Test void all303OriginalSourcesAndRepeatedAssemblyRemainIntact()throws Exception{
        var filter=new ReadmeKnowledgeFilter();int count=0,body=0,nav=0;
        for(var row:JSON.readTree(Files.readString(root.resolve("_experiment_staging/readme-filter-v1-20261029/snapshot.json"))).path("documents")){
            Source s=new Source(row.path("documentId").asText(),row.path("versionId").asText(),row.path("sourceRef").asText(),row.path("content").asText());
            var p=filter.preview(s);assertEquals(row.path("sha256").asText(),p.sourceSha256());assertEquals(p,filter.reassemble(s,p,p.blocks()));
            var out=new ReadmeRecommendationProjection().project(s,p,List.of());ReadmeRecommendationProjection.verify(s,out.preview(),out.projection());
            assertEquals(s.text(),p.blocks().stream().map(Block::text).reduce("",String::concat));
            body+=out.projection().knowledgeCandidates().size();nav+=out.projection().pointers().size();count++;
        }
        assertEquals(303,count);
        write("corpus-check.json",Map.of("documents",count,"bodyPackets",body,"navigationLinks",nav,"sourceMappingErrors",0,
                "idempotenceErrors",0,"dispositionLeaks",0,"providerCalls",0,"indexWrites",0));
    }
}
