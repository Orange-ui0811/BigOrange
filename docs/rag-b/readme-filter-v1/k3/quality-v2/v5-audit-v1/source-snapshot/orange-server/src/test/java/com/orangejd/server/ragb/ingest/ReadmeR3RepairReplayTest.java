package com.orangejd.server.ragb.ingest;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;
import static com.orangejd.server.ragb.ingest.ReadmeModelReview.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reuse only the previously accepted selections, without pretending to run the new prompt/model. */
@EnabledIfSystemProperty(named="readme.repair.root",matches=".+")
class ReadmeR3RepairReplayTest {
    private static final ObjectMapper JSON=new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path root=Path.of(System.getProperty("readme.repair.root"));
    private final Path quality=root.resolve("docs/rag-b/readme-filter-v1/k3/quality-v2");
    private final Path stage=root.resolve("_experiment_staging/readme-r3-repair-v2");
    private void write(String name,Object value)throws Exception{Files.createDirectories(stage);Files.writeString(stage.resolve(name),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value)+"\n");}

    @Test void replayR5AcceptedSelectionsAgainstCurrentRulesWithoutTransport()throws Exception{
        int accepted=0,used=0;List<Map<String,Object>> results=new ArrayList<>();List<String> superseded=new ArrayList<>();
        for(var row:JSON.readTree(Files.readString(quality.resolve("r4/sources.json"))).path("documents")){
            Source source=JSON.treeToValue(row.path("source"),Source.class);
            var filter=new ReadmeKnowledgeFilter();var preview=filter.preview(source);
            var old=JSON.readTree(Files.readString(quality.resolve("r5/results/primary/"+source.documentId()+".json"))).path("result");
            Map<String,Block> originals=new LinkedHashMap<>();preview.blocks().forEach(b->originals.put(b.blockId(),b));
            List<Selection> choices=new ArrayList<>();
            for(var node:old.path("selections")){
                var selection=JSON.treeToValue(node,Selection.class);accepted++;
                assertTrue(originals.containsKey(selection.blockId()));
                assertTrue(selection.evidenceBlockIds().stream().allMatch(originals::containsKey));
                Block current=originals.get(selection.blockId());
                if(current.decision()!=Decision.REVIEW&&(selection.decision()!=current.decision())) {
                    superseded.add(selection.blockId());continue;
                }
                choices.add(selection);
            }
            used+=choices.size();var out=ReadmeModelReview.assembleSelections(source,preview,choices,"R5_ACCEPTED_SELECTIONS_REPLAY_ONLY","R3_REPAIR_V2_OFFLINE");
            ReadmeRecommendationProjection.verify(source,out.preview(),out.projection());
            assertEquals(source.text(),out.preview().blocks().stream().map(Block::text).reduce("",String::concat));
            Map<String,Object> result=Map.of("preview",out.preview(),"recommendation",out.projection(),"selections",choices);
            results.add(Map.of("documentId",source.documentId(),"result",result));
        }
        assertEquals(12,results.size());assertEquals(119,accepted);
        write("replay-results.json",Map.of("mode","OLD_ACCEPTED_SELECTIONS_NEW_RULES_DIAGNOSTIC","documents",results,
                "oldAcceptedSelections",accepted,"reusedSelections",used,"supersededByFixedRule",superseded,"additionalProviderCalls",0,"indexWrites",0));
    }

    @Test void all303SourcesRemainMappedAndRepeatedAssemblyIsStable()throws Exception{
        var corpus=JSON.readTree(Files.readString(root.resolve("_experiment_staging/readme-filter-v1-20261029/snapshot.json")));
        int count=0,body=0,nav=0;var filter=new ReadmeKnowledgeFilter();
        for(var row:corpus.path("documents")){
            Source source=new Source(row.path("documentId").asText(),row.path("versionId").asText(),row.path("sourceRef").asText(),row.path("content").asText());
            var p=filter.preview(source);assertEquals(row.path("sha256").asText(),p.sourceSha256());
            assertEquals(p,filter.reassemble(source,p,p.blocks()));
            var out=new ReadmeRecommendationProjection().project(source,p,List.of());
            ReadmeRecommendationProjection.verify(source,out.preview(),out.projection());
            body+=out.projection().knowledgeCandidates().size();nav+=out.projection().pointers().size();count++;
        }
        assertEquals(303,count);
        write("corpus-check.json",Map.of("documents",count,"bodyPackets",body,"navigationLinks",nav,
                "sourceMappingErrors",0,"dispositionLeaks",0,"idempotenceErrors",0,"additionalProviderCalls",0,"indexWrites",0));
    }
}
