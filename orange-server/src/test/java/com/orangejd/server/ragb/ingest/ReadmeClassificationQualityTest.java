package com.orangejd.server.ragb.ingest;

import com.fasterxml.jackson.databind.*;
import com.orangejd.server.foundation.external.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Quality dataset preparation only; no provider or Spring application bootstrap. */
@EnabledIfSystemProperty(named="readme.quality.root",matches=".+")
class ReadmeClassificationQualityTest {
    @Test void prepareRepresentativeBatchPlan() throws Exception {
        Path root=Path.of(System.getProperty("readme.quality.root"));
        ObjectMapper json=new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        Path out=root.resolve("_experiment_staging/readme-quality-v1");Files.createDirectories(out);
        var corpus=json.readTree(Files.readString(root.resolve("_experiment_staging/readme-filter-v1-20261029/snapshot.json")));
        var selection=json.readTree(Files.readString(root.resolve("docs/rag-b/readme-filter-v1/k3/quality-v1/selection.json")));
        Map<String,JsonNode> sources=new LinkedHashMap<>();corpus.path("documents").forEach(d->sources.put(d.path("documentId").asText(),d));
        List<Map<String,Object>> rows=new ArrayList<>();
        for(JsonNode item:selection.path("documents")) {
            JsonNode doc=sources.get(item.path("documentId").asText());assertNotNull(doc);
            var source=new ReadmeKnowledgeFilter.Source(doc.path("documentId").asText(),doc.path("versionId").asText(),doc.path("sourceRef").asText(),doc.path("content").asText());
            var preview=new ReadmeKnowledgeFilter().preview(source);
            var engine=new ReadmeModelReview("QUALITY_PLAN_ONLY",ReadmeModelReview.Limits.offline(),out.resolve("cache"),
                    new ReadmeReviewJournal(out.resolve("unused-plan-ledger.jsonl"),"plan",true),(s,u,o)->{fail("No provider calls allowed");return null;});
            var batches=engine.plan(source,preview);
            rows.add(Map.of("documentId",source.documentId(),"versionId",source.versionId(),"sourceRef",source.sourceRef(),
                    "sourceSha256",preview.sourceSha256(),"cohort",item.path("cohort").asText(),"focus",item.path("focus").asText(),
                    "blocks",preview.blocks(),"batches",batches));
        }
        assertEquals(16,rows.size());
        Files.writeString(out.resolve("all-plans.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("documents",rows,"providerCalls",0)));
    }
    @Test
    @EnabledIfSystemProperty(named="readme.quality.live",matches="true")
    void executeApprovedFrozenBatches() throws Exception {
        Path root=Path.of(System.getProperty("readme.quality.root"));
        Path stage=root.resolve("_experiment_staging/readme-quality-v1"),docs=root.resolve("docs/rag-b/readme-filter-v1/k3/quality-v1");
        Files.createDirectories(stage.resolve("results"));Files.createDirectories(stage.resolve("traces"));
        ObjectMapper json=new ObjectMapper();String rawDataset=Files.readString(docs.resolve("frozen-dataset.json"));
        JsonNode dataset=json.readTree(rawDataset),approval=json.readTree(Files.readString(docs.resolve("authorization.json")));
        assertTrue(approval.path("authorized").asBoolean());assertEquals(20,approval.path("maximumAttempts").asInt());
        assertEquals(ReadmeKnowledgeFilter.sha256(rawDataset),approval.path("datasetSha256").asText());
        assertEquals("20",System.getenv("CUSTOMER_ACCEPTANCE_LIMIT"));
        assertTrue(required("CUSTOMER_ACCEPTANCE_LEDGER").endsWith("quality-v1/provider-calls.jsonl"));
        String endpoint=required("README_K3_ENDPOINT"),key=required("README_K3_KEY"),model=required("README_K3_MODEL");
        String identity=model+":"+ReadmeKnowledgeFilter.sha256(endpoint+"\0"+key+"\0"+model);
        var config=new AiRuntimeConfig("readme-quality","env",endpoint,key,model,"ENV_QUALITY");
        Map<String,JsonNode> corpus=new LinkedHashMap<>();
        json.readTree(Files.readString(root.resolve("_experiment_staging/readme-filter-v1-20261029/snapshot.json"))).path("documents")
                .forEach(d->corpus.put(d.path("documentId").asText(),d));
        Set<String> retryIds=new HashSet<>();String retry=System.getProperty("readme.quality.retry-ids","");
        if(!retry.isBlank())retryIds.addAll(Arrays.asList(retry.split(",")));
        for(JsonNode sample:dataset.path("documents")) {
            String id=sample.path("documentId").asText();if(!retryIds.isEmpty()&&!retryIds.contains(id))continue;
            String phase=retryIds.isEmpty()?"primary":"retry";Path target=stage.resolve("results/"+id+"-"+phase+".json");
            if(Files.exists(target))continue; // Resume never silently spends another attempt for a completed sample.
            JsonNode doc=corpus.get(id);assertNotNull(doc);
            var source=new ReadmeKnowledgeFilter.Source(id,doc.path("versionId").asText(),doc.path("sourceRef").asText(),doc.path("content").asText());
            assertEquals(sample.path("sourceSha256").asText(),ReadmeKnowledgeFilter.sha256(source.text()));
            assertEquals(sample.path("versionId").asText(),source.versionId());
            var preview=new ReadmeKnowledgeFilter().preview(source);AtomicInteger traceNumber=new AtomicInteger();
            var adapter=new CustomerSpringAiChatModel(config,35,2048,false,false,16000,20000,(event,body)->{
                try {
                    if(event.equals("MODEL_REQUEST")) {
                        Path ledger=Path.of(required("CUSTOMER_ACCEPTANCE_LEDGER"));
                        traceNumber.set((int)(Files.exists(ledger)?Files.readAllLines(ledger).stream().filter(l->!l.isBlank()).count():0)+1);
                    }
                    String file=String.format(Locale.ROOT,"%02d-%s-%s.json",traceNumber.get(),id,event);
                    Files.writeString(stage.resolve("traces").resolve(file),json.writerWithDefaultPrettyPrinter().writeValueAsString(body),StandardOpenOption.CREATE_NEW);
                }catch(Exception ex){throw new IllegalStateException("QUALITY_TRACE_WRITE_FAILED");}
            });
            var engine=new ReadmeModelReview(identity,new ReadmeModelReview.Limits(20,12000,2048,4,0,280960),stage.resolve("cache"),
                    new ReadmeReviewJournal(stage.resolve("selection-attempts.jsonl"),"quality-v1-authorized20",false),new ReadmeSpringAiReviewTransport(adapter));
            List<String> wanted=new ArrayList<>();sample.path("targetIds").forEach(n->wanted.add(n.asText()));
            var batch=engine.plan(source,preview).stream().filter(b->b.targetIds().equals(wanted)).findFirst().orElseThrow();
            var result=engine.review(source,preview,Set.of(batch.cacheKey()));
            Files.writeString(target,json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("phase",phase,"datasetSha256",approval.path("datasetSha256").asText(),
                    "policy",ReadmeModelReview.POLICY,"prompt",ReadmeModelReview.PROMPT,"modelIdentity",identity,"result",result)),StandardOpenOption.CREATE_NEW);
        }
    }
    private String required(String name) {String value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalStateException("Missing "+name);return value;}
}
