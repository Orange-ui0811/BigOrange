package com.orangejd.server.ragb.ingest;

import com.fasterxml.jackson.databind.*;
import com.orangejd.server.foundation.external.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;

/** Frozen V5 pilot acquisition and replay. No application/database/index bootstrap. */
@EnabledIfSystemProperty(named="readme.v5.pilot.root", matches=".+")
class K3V5PilotAcceptanceTest {
    private static final ObjectMapper JSON=new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final String R4="docs/rag-b/readme-filter-v1/k3/quality-v2/v5-pilot-v3/";
    private static final String R5="docs/rag-b/readme-filter-v1/k3/quality-v2/v5-pilot-v3/";
    private static final String RUN="K3_V5_PILOT_V3_20261030";
    private static final int MAX=18;
    private final Path root=Path.of(System.getProperty("readme.v5.pilot.root"));
    @TempDir Path temp;

    private JsonNode read(Path path)throws Exception{return JSON.readTree(Files.readString(path));}
    private JsonNode frozen(String file)throws Exception{return read(root.resolve(R4+file));}
    private ReadmeModelReview.Limits limits(int count){return new ReadmeModelReview.Limits(count,12000,2048,8,0,252864);}
    private ReadmeModelReview engine(Path cache,Path journal,String run,boolean simulated,ReadmeModelReview.Transport transport)throws Exception{
        return new ReadmeModelReview(frozen("provider-config.json").path("modelIdentity").asText(),limits(MAX),cache,
                new ReadmeReviewJournal(journal,run,simulated),transport);
    }
    private void hashes(JsonNode values)throws Exception{
        for(var fields=values.fields();fields.hasNext();){var e=fields.next();
            assertEquals(e.getValue().asText(),sha256(Files.readString(root.resolve(e.getKey()))),"Frozen file changed: "+e.getKey());}
    }
    private void checkFreeze()throws Exception{
        hashes(frozen("artifact-manifest.json").path("files"));
        hashes(read(root.resolve("docs/rag-b/readme-filter-v1/k3/quality-v2/v5-admission-repair-v2/before.json")).path("historicalFiles"));
    }
    private void checkAuthorization()throws Exception{
        checkFreeze();var auth=read(root.resolve(R5+"authorization.json"));
        assertTrue(auth.path("authorized").asBoolean());assertEquals(MAX,auth.path("maximumAttempts").asInt());
        assertEquals(RUN,auth.path("runId").asText());
        assertEquals(sha256(Files.readString(root.resolve(R4+"artifact-manifest.json"))),auth.path("sealSha256").asText());
        assertEquals(sha256(Files.readString(root.resolve(R4+"frozen-labels.json"))),auth.path("datasetSha256").asText());
        assertEquals(sha256(Files.readString(root.resolve(R4+"requests.json"))),auth.path("requestsSha256").asText());
        assertEquals(sha256(Files.readString(root.resolve(R5+"execution-manifest.json"))),auth.path("executionManifestSha256").asText());
        hashes(read(root.resolve(R5+"execution-manifest.json")).path("files"));
    }
    private LinkedHashMap<String,Source> sources()throws Exception{
        var result=new LinkedHashMap<String,Source>();
        for(var row:frozen("sources.json").path("documents")){var s=JSON.treeToValue(row.path("source"),Source.class);result.put(s.documentId(),s);}
        return result;
    }
    private List<JsonNode> requests()throws Exception{
        var result=new ArrayList<JsonNode>();frozen("requests.json").path("batches").forEach(result::add);return result;
    }
    private void samePlan(ReadmeModelReview e,Source s)throws Exception{
        var actual=e.plan(s,new ReadmeKnowledgeFilter().preview(s));
        var expected=requests().stream().filter(r->r.path("documentId").asText().equals(s.documentId())).toList();
        assertEquals(expected.size(),actual.size());
        for(int i=0;i<actual.size();i++){
            var node=JSON.valueToTree(actual.get(i));for(var names=node.fieldNames();names.hasNext();){String name=names.next();assertEquals(expected.get(i).path(name),node.path(name),name);}
        }
    }
    private static final class BeforeHttp extends RuntimeException{}
    private static void guardWire(JsonNode request,Map<String,Object> body,String model){
        assertEquals(Set.of("model","temperature","max_tokens","messages","thinking","response_format"),body.keySet());
        assertEquals(model,body.get("model"));assertEquals(0.0,body.get("temperature"));assertEquals(2048,body.get("max_tokens"));
        assertEquals(Map.of("type","disabled"),body.get("thinking"));assertEquals(Map.of("type","json_object"),body.get("response_format"));
        assertEquals(List.of(Map.of("role","system","content",request.path("system").asText()),
                Map.of("role","user","content",request.path("user").asText())),body.get("messages"));
    }
    private void durable(Path path,Object value)throws Exception{
        Files.createDirectories(path.getParent());Path staging=Files.createTempFile(path.getParent(),"r5-",".tmp");
        try{
            try(var channel=FileChannel.open(staging,StandardOpenOption.WRITE)){
                ByteBuffer bytes=java.nio.charset.StandardCharsets.UTF_8.encode(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value)+"\n");
                while(bytes.hasRemaining())channel.write(bytes);channel.force(true);
            }
            try{Files.move(staging,path,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(staging,path);}
        }finally{Files.deleteIfExists(staging);}
    }
    private ReadmeModelReview.Reply uncertainReply(JsonNode request)throws Exception{
        List<Map<String,Object>> selections=new ArrayList<>();
        for(var bid:request.path("targetIds"))selections.add(Map.of("blockId",bid.asText(),"decision","REVIEW","kind","OTHER",
                "scope","UNKNOWN","reason","UNCERTAIN","applicability","UNKNOWN","evidenceBlockIds",List.of(bid.asText())));
        return new ReadmeModelReview.Reply(JSON.writeValueAsString(Map.of("schemaVersion",ReadmeModelReview.SCHEMA,"selections",selections)),"stop",Map.of());
    }

    @Test void frozenPlansWireAndAllBatchMergeWithoutNetwork()throws Exception{
        checkFreeze();var config=frozen("provider-config.json");var reqs=requests();int[] wireChecks={0};
        for(var request:reqs){
            var runtime=new AiRuntimeConfig("r5-offline","env",config.path("endpoint").asText(),"OFFLINE_NOT_A_KEY",config.path("model").asText(),"TEST");
            var driver=new CustomerSpringAiChatModel(runtime,35,2048,false,false,16000,20000,(kind,body)->{
                assertEquals("MODEL_REQUEST",kind);guardWire(request,body,config.path("model").asText());wireChecks[0]++;throw new BeforeHttp();});
            assertThrows(BeforeHttp.class,()->new ReadmeSpringAiReviewTransport(driver).call(request.path("system").asText(),request.path("user").asText(),2048));
        }
        assertEquals(16,wireChecks[0]);int choices=0;int batches=0;
        for(var s:sources().values()){
            var e=engine(temp.resolve("cache"),temp.resolve("simulated.jsonl"),"OFFLINE_MERGE",true,(sys,user,out)->{
                var request=reqs.stream().filter(r->r.path("system").asText().equals(sys)&&r.path("user").asText().equals(user)).findFirst().orElseThrow();
                return uncertainReply(request);
            });
            samePlan(e,s);var result=e.review(s,new ReadmeKnowledgeFilter().preview(s));
            assertTrue(result.attempts().stream().allMatch(a->a.status().equals("MODEL_VALIDATED")));
            ReadmeRecommendationProjection.verify(s,result.preview(),result.recommendation());
            choices+=result.selections().size();batches+=result.batches().size();
        }
        assertEquals(78,choices);assertEquals(16,batches);
        Path receipt=root.resolve("_experiment_staging/readme-v5-pilot-v3/offline-preflight.json");
        Files.createDirectories(receipt.getParent());Files.writeString(receipt,JSON.writeValueAsString(Map.of("requests",16,"modelTargets",78,
                "exactWireChecks",wireChecks[0],"mergedRepositories",5,"providerCalls",0,"indexWrites",0,"sealedFilesVerified",true))+"\n");
    }

    @Test void rejectedBatchRemainsRejectedOnOfflineReplay()throws Exception{
        Source s=sources().values().iterator().next();JsonNode req=requests().getFirst();
        var e=engine(temp.resolve("bad-cache"),temp.resolve("bad.jsonl"),"OFFLINE_BAD",true,
                (sys,user,out)->new ReadmeModelReview.Reply("{}","stop",Map.of()));
        var result=e.review(s,new ReadmeKnowledgeFilter().preview(s),Set.of(req.path("cacheKey").asText()));
        assertEquals(1,result.attempts().size());assertTrue(result.attempts().getFirst().status().startsWith("OUTPUT_REJECTED_"));
        assertTrue(result.selections().isEmpty());assertFalse(Files.exists(temp.resolve("bad-cache").resolve(req.path("cacheKey").asText()+".json")));
    }

    @Test @EnabledIfSystemProperty(named="readme.v5.pilot.live",matches="true")
    void acquireApprovedRequestsThenReplayAllRepositories()throws Exception{
        checkAuthorization();String phase=System.getProperty("readme.v5.pilot.phase","primary");
        assertTrue(Set.of("primary","reserve","replay").contains(phase));
        Path output=root.resolve(R5);var reqs=requests();var sources=sources();
        String endpoint=System.getenv("README_K3_ENDPOINT"),key=System.getenv("README_K3_KEY"),model=System.getenv("README_K3_MODEL");
        if(!phase.equals("replay")){
            var config=frozen("provider-config.json");assertNotNull(key,"Existing model configuration missing (value withheld)");
            assertEquals(config.path("modelIdentity").asText(),model+":"+sha256(endpoint+"\0"+key+"\0"+model),"Runtime model fingerprint differs (values withheld)");
            assertEquals(root.resolve(R5+"provider-calls.jsonl").toString(),System.getenv("CUSTOMER_ACCEPTANCE_LEDGER"));
            assertEquals("18",System.getenv("CUSTOMER_ACCEPTANCE_LIMIT"));
            List<JsonNode> selected=reqs;
            if(phase.equals("reserve")){
                assertTrue(Files.exists(output.resolve("primary-evaluation.json")),"Score the immutable primary outcomes before reserve calls");
                List<JsonNode> failed=new ArrayList<>();
                for(var r:reqs){Path attempt=output.resolve("attempts/primary/"+r.path("requestId").asText());
                    assertTrue(Files.exists(attempt.resolve("reservation.json")),"Primary pass incomplete");
                    if(Files.exists(attempt.resolve("outcome.json"))){String status=read(attempt.resolve("outcome.json")).path("status").asText();
                        if(status.equals("TIMEOUT")||status.equals("PROVIDER_OR_STORAGE_FAILURE")||status.startsWith("OUTPUT_REJECTED_"))failed.add(r);}
                }
                selected=failed.stream().limit(2).toList();
            }
            for(var r:selected){
                checkAuthorization();String id=r.path("requestId").asText();Path attempt=output.resolve("attempts/"+phase+"/"+id);
                // A reservation survives crashes: never silently repeat an uncertain or completed primary HTTP call.
                if(Files.exists(attempt.resolve("reservation.json")))continue;
                durable(attempt.resolve("reservation.json"),Map.of("requestId",id,"cacheKey",r.path("cacheKey").asText(),"phase",phase,
                        "runId",RUN,"at",Instant.now().toString(),"datasetSha256",sha256(Files.readString(root.resolve(R4+"frozen-labels.json")))));
                var runtime=new AiRuntimeConfig("readme-v5-pilot-v3","env",endpoint,key,model,"ENV_QUALITY");
                var driver=new CustomerSpringAiChatModel(runtime,35,2048,false,false,16000,20000,(kind,body)->{
                    if(kind.equals("MODEL_REQUEST"))guardWire(r,body,model);
                    try{durable(attempt.resolve(kind.equals("MODEL_REQUEST")?"wire-request.json":"provider-response.json"),body);}
                    catch(Exception ex){throw new IllegalStateException("R5_EVIDENCE_STORAGE_FAILURE");}
                });
                var live=new ReadmeSpringAiReviewTransport(driver);long started=System.nanoTime();
                ReadmeModelReview.Transport transport=(sys,user,out)->{
                    assertEquals(r.path("system").asText(),sys);assertEquals(r.path("user").asText(),user);assertEquals(2048,out);
                    var reply=live.call(sys,user,out);durable(attempt.resolve("reply.json"),reply);return reply;
                };
                var e=engine(attempt.resolve("validated-cache"),output.resolve("selection-attempts.jsonl"),RUN,false,transport);
                var s=sources.get(r.path("documentId").asText());samePlan(e,s);
                var result=e.review(s,new ReadmeKnowledgeFilter().preview(s),Set.of(r.path("cacheKey").asText()));
                assertEquals(1,result.attempts().size());
                durable(attempt.resolve("outcome.json"),Map.of("requestId",id,"cacheKey",r.path("cacheKey").asText(),"phase",phase,
                        "status",result.attempts().getFirst().status(),"attempts",result.attempts(),"selections",result.selections(),
                        "elapsedMs",(System.nanoTime()-started)/1_000_000));
                System.out.println("R5 "+phase+" "+id+" "+result.attempts().getFirst().status());
            }
        }
        replay("primary",reqs,sources,output);
        if(!phase.equals("primary"))replay("diagnostic",reqs,sources,output);
    }
    private void replay(String phase,List<JsonNode> reqs,Map<String,Source> sources,Path output)throws Exception{
        Path destination=output.resolve("results/"+phase);Files.createDirectories(destination);
        for(var s:sources.values()){
            Path resultFile=destination.resolve(s.documentId()+".json");if(Files.exists(resultFile))continue;
            var e=engine(temp.resolve("replay-cache-"+phase),temp.resolve("replay-"+phase+".jsonl"),RUN+"_"+phase,true,(sys,user,out)->{
                var r=reqs.stream().filter(x->x.path("documentId").asText().equals(s.documentId())&&x.path("user").asText().equals(user)).findFirst().orElseThrow();
                Path primary=output.resolve("attempts/primary/"+r.path("requestId").asText());Path attempt=primary;
                assertTrue(Files.exists(primary.resolve("reservation.json")),"Incomplete primary acquisition; replay not frozen");
                if(phase.equals("diagnostic")){
                    Path retry=output.resolve("attempts/reserve/"+r.path("requestId").asText());
                    if(Files.exists(retry.resolve("outcome.json"))&&read(retry.resolve("outcome.json")).path("status").asText().equals("MODEL_VALIDATED"))attempt=retry;
                }
                if(!Files.exists(attempt.resolve("outcome.json"))||!Files.exists(attempt.resolve("reply.json")))throw new IllegalStateException("NO_COMPLETED_PRIMARY_REPLY");
                return JSON.treeToValue(read(attempt.resolve("reply.json")),ReadmeModelReview.Reply.class);
            });
            samePlan(e,s);var result=e.review(s,new ReadmeKnowledgeFilter().preview(s));
            ReadmeRecommendationProjection.verify(s,result.preview(),result.recommendation());
            durable(resultFile,Map.of("phase",phase,"datasetSha256",sha256(Files.readString(root.resolve(R4+"frozen-labels.json"))),
                    "sealSha256",sha256(Files.readString(root.resolve(R4+"artifact-manifest.json"))),"result",result,"replayNetworkCalls",0));
        }
    }
}
