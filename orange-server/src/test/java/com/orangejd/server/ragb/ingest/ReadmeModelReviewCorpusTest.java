package com.orangejd.server.ragb.ingest;

import com.fasterxml.jackson.databind.*;
import com.orangejd.server.foundation.external.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Frozen local corpus planning; separate explicit opt-in for at most two smoke requests. */
@EnabledIfSystemProperty(named="readme.k3.root",matches=".+")
class ReadmeModelReviewCorpusTest {
    private final ObjectMapper json=new ObjectMapper();
    @Test void planAllFrozenRepositoriesWithoutProviderCalls() throws Exception {
        Path root=Path.of(System.getProperty("readme.k3.root")),out=root.resolve("_experiment_staging/readme-filter-k3");
        Files.createDirectories(out);var snapshot=json.readTree(Files.readString(root.resolve("_experiment_staging/readme-filter-v1-20261029/snapshot.json")));
        Set<String> selected=new HashSet<>();json.readTree(Files.readString(root.resolve("docs/rag-b/readme-filter-v1/sample-manifest.json"))).path("samples").forEach(n->selected.add(n.path("documentId").asText()));
        List<Map<String,Object>> docs=new ArrayList<>();int targets=0,eligible=0,hard=0;
        for(JsonNode doc:snapshot.path("documents"))if(selected.contains(doc.path("documentId").asText())) {
            var source=source(doc);var preview=new ReadmeKnowledgeFilter().preview(source);
            var engine=new ReadmeModelReview("PLANNING_ONLY",ReadmeModelReview.Limits.offline(),out.resolve("cache"),
                    new ReadmeReviewJournal(out.resolve("plan-journal.jsonl"),"plan",true),(s,u,o)->{fail("Planning must not call provider");return null;});
            var plan=engine.plan(source,preview);
            int soft=(int)preview.blocks().stream().filter(ReadmeModelReview::eligible).count();eligible+=soft;
            hard+=preview.reviewBlockIds().size()-soft;
            int planned=plan.stream().mapToInt(b->b.targetIds().size()).sum();targets+=planned;
            docs.add(Map.of("documentId",source.documentId(),"sourceRef",source.sourceRef(),"eligibleBlocks",soft,"plannedTargets",planned,
                    "oversizeDeferred",soft-planned,"batches",plan));
        }
        assertEquals(38,docs.size());
        Files.writeString(out.resolve("request-plan.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "policy",ReadmeModelReview.POLICY,"documents",docs,"eligibleBlocks",eligible,"hardReviewBlocks",hard,"plannedTargets",targets,"providerCalls",0)));
    }
    @Test
    @EnabledIfSystemProperty(named="readme.k3.live",matches="true")
    void liveSmokeWithinExistingTwoAttemptBudget() throws Exception {
        Path root=Path.of(System.getProperty("readme.k3.root")),out=root.resolve("_experiment_staging/readme-filter-k3");Files.createDirectories(out);
        String endpoint=required("README_K3_ENDPOINT"),key=required("README_K3_KEY"),model=required("README_K3_MODEL");
        assertEquals("40",System.getenv("CUSTOMER_ACCEPTANCE_LIMIT"));
        assertTrue(required("CUSTOMER_ACCEPTANCE_LEDGER").endsWith("context-v3/live-provider-calls.jsonl"));
        var config=new AiRuntimeConfig("readme-review","env",endpoint,key,model,"ENV_SMOKE");
        var adapter=new CustomerSpringAiChatModel(config,35,2048,false,false,16000,20000,(event,body)->{});
        var snapshot=json.readTree(Files.readString(root.resolve("_experiment_staging/readme-filter-v1-20261029/snapshot.json")));
        List<ReadmeModelReview.Result> results=new ArrayList<>();
        for(String id:List.of("335","517")) {
            JsonNode doc=null;for(JsonNode d:snapshot.path("documents"))if(id.equals(d.path("documentId").asText()))doc=d;
            assertNotNull(doc);var source=source(doc);var preview=new ReadmeKnowledgeFilter().preview(source);
            var limits=new ReadmeModelReview.Limits(1,12000,2048,4,0,16000);
            var engine=new ReadmeModelReview(model+":"+ReadmeKnowledgeFilter.sha256(endpoint+"\0"+key+"\0"+model),limits,out.resolve("live-cache"),
                    new ReadmeReviewJournal(out.resolve("provider-calls.jsonl"),"k3-smoke-20261029-"+id,false),new ReadmeSpringAiReviewTransport(adapter));
            results.add(engine.review(source,preview));
        }
        Files.writeString(out.resolve("live-results.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(results));
        // Quality outcomes are recorded, not asserted into a passing accuracy claim.
        assertEquals(2,results.size());
    }
    private ReadmeKnowledgeFilter.Source source(JsonNode d) {
        return new ReadmeKnowledgeFilter.Source(d.path("documentId").asText(),d.path("versionId").asText(),d.path("sourceRef").asText(),d.path("content").asText());
    }
    @Test
    @EnabledIfSystemProperty(named="readme.k3.cache-replay",matches="true")
    void replayRealValidatedCacheWithNoProviderAccess() throws Exception {
        Path root=Path.of(System.getProperty("readme.k3.root")),out=root.resolve("_experiment_staging/readme-filter-k3");
        var live=json.readTree(Files.readString(out.resolve("live-results.json")));
        JsonNode successful=live.get(1);String cacheKey=successful.path("batches").get(0).path("cacheKey").asText();
        JsonNode oldCache=json.readTree(Files.readString(out.resolve("live-cache").resolve(cacheKey+".json")));
        String identity=oldCache.path("modelIdentity").asText();
        var snapshot=json.readTree(Files.readString(root.resolve("_experiment_staging/readme-filter-v1-20261029/snapshot.json")));
        JsonNode selected=null;for(JsonNode d:snapshot.path("documents"))if("517".equals(d.path("documentId").asText()))selected=d;
        assertNotNull(selected);var source=source(selected);var preview=new ReadmeKnowledgeFilter().preview(source);
        String journalBefore=Files.readString(out.resolve("provider-calls.jsonl"));
        var engine=new ReadmeModelReview(identity,new ReadmeModelReview.Limits(1,12000,2048,4,0,16000),out.resolve("live-cache"),
                new ReadmeReviewJournal(out.resolve("provider-calls.jsonl"),"k3-smoke-20261029-517",false),
                (s,u,o)->{fail("No provider may be reached during cache replay");return null;});
        var canonical=engine.plan(source,preview).getFirst();
        if(!successful.path("batches").get(0).path("system").asText().equals(canonical.system())) {
            // A newer prompt must invalidate the old real cache. With the old run exhausted, defer without HTTP.
            var invalidated=engine.review(source,preview);
            assertTrue(invalidated.selections().isEmpty());
            assertTrue(invalidated.attempts().stream().allMatch(a->a.status().equals("BUDGET_EXHAUSTED")));
            assertEquals(journalBefore,Files.readString(out.resolve("provider-calls.jsonl")));
            Files.writeString(out.resolve("cache-policy-invalidation.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                    "newPromptInvalidatesOldCache",true,"providerCalls",0,"journalUnchanged",true)));
            return;
        }
        // Preserve the real receipt. Import only when the old and canonical requests are structurally identical.
        // This migration is specific to the recorded fixture; ordinary cache loads remain exact-key matches.
        assertEquals(json.readTree(successful.path("batches").get(0).path("user").asText()),json.readTree(canonical.user()));
        assertEquals(successful.path("batches").get(0).path("system").asText(),canonical.system());
        assertEquals(cacheKey,oldCache.path("cacheKey").asText());
        assertEquals(ReadmeKnowledgeFilter.sha256(oldCache.path("response").asText()),oldCache.path("responseSha256").asText());
        Path canonicalFile=out.resolve("live-cache").resolve(canonical.cacheKey()+".json");
        if(!Files.exists(canonicalFile)) {
            var imported=oldCache.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)imported).put("cacheKey",canonical.cacheKey());
            Files.writeString(canonicalFile,json.writeValueAsString(imported),StandardOpenOption.CREATE_NEW);
        }
        var result=engine.review(source,preview);
        assertTrue(result.attempts().getFirst().cached());assertEquals(4,result.selections().size());
        assertEquals(journalBefore,Files.readString(out.resolve("provider-calls.jsonl")));
        Files.writeString(out.resolve("cache-replay.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "cachedSelections",result.selections().size(),"providerCalls",0,"journalUnchanged",true,
                "canonicalCacheKey",canonical.cacheKey(),"originalReceiptCacheKey",cacheKey,"structurallyIdenticalRequest",true)));
    }
    private String required(String name) {String value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalStateException("Missing "+name);return value;}
}
