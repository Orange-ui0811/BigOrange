package com.orangejd.server.ragb.ingest;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;
import static com.orangejd.server.ragb.ingest.ReadmeModelReview.*;
import static org.junit.jupiter.api.Assertions.*;

class ReadmeModelReviewTest {
    @TempDir Path temp;
    private static final ObjectMapper JSON=new ObjectMapper();
    private final Source source=new Source("one","v1","owner/tool","# Tool\n未来可以深入研究这里的代码组织。\n\n另一段尚未分类的说明。\n");
    private Preview initial() {return new ReadmeKnowledgeFilter().preview(source);}
    private ReadmeModelReview engine(String run,String model,Limits limits,Transport transport) {
        return new ReadmeModelReview(model,limits,temp.resolve("cache"),new ReadmeReviewJournal(temp.resolve("ledger.jsonl"),run,true),transport);
    }
    private Limits limits(int max,int retries) {return new Limits(max,12000,2048,4,retries,100000);}
    private String answer(String user,String decision) throws Exception {
        List<Map<String,Object>> out=new ArrayList<>();for(JsonNode b:JSON.readTree(user).path("targets"))out.add(Map.of("blockId",b.path("blockId").asText(),
                "decision",decision,"kind","TECHNICAL_TEXT","scope","CORE","reason",decision.equals("DROP")?"NOISE":decision.equals("KEEP")?"KNOWLEDGE":"UNCERTAIN",
                "applicability",decision.equals("KEEP")?"IN_SCOPE":"UNKNOWN","evidenceBlockIds",List.of(b.path("blockId").asText())));
        return JSON.writeValueAsString(Map.of("schemaVersion",SCHEMA,"selections",out));
    }
    private Reply valid(String user) throws Exception {return new Reply(answer(user,"KEEP"),"stop",Map.of("prompt_tokens",12,"completion_tokens",8));}
    private long starts() throws Exception {
        Path file=temp.resolve("ledger.jsonl");if(!Files.exists(file))return 0;
        return Files.readAllLines(file).stream().filter(s->s.contains("\"event\":\"START\"")).count();
    }
    @Test void modelSelectsOriginalAndPacketsRetainRawOffsets() throws Exception {
        var result=engine("a","m1",limits(1,0),(s,u,o)->valid(u)).review(source,initial());
        assertFalse(result.selections().isEmpty());assertFalse(result.preview().knowledgeCandidates().isEmpty());
        assertEquals(source.text(),result.preview().blocks().stream().map(Block::text).reduce("",String::concat));
        assertTrue(result.preview().blocks().stream().anyMatch(b->b.reason().equals("MODEL_KNOWLEDGE")));
        verify(source,result.preview());assertEquals(1,starts());
    }
    @Test void cachedRepeatMakesNoNewRequestAndIsRevalidated() throws Exception {
        AtomicInteger calls=new AtomicInteger();Transport t=(s,u,o)->{calls.incrementAndGet();return valid(u);};
        engine("a","m1",limits(1,0),t).review(source,initial());
        var cached=engine("a","m1",limits(1,0),t).review(source,initial());
        assertEquals(1,calls.get());assertTrue(cached.attempts().getFirst().cached());assertEquals(1,starts());
    }
    @Test void sourceVersionModelAndOutputPolicyInvalidateCache() throws Exception {
        AtomicInteger calls=new AtomicInteger();Transport t=(s,u,o)->{calls.incrementAndGet();return valid(u);};
        var base=engine("a","m1",limits(10,0),t);base.review(source,initial());
        engine("a","m2",limits(10,0),t).review(source,initial());
        var changed=new Source("one","v2","owner/tool",source.text());
        base.review(changed,new ReadmeKnowledgeFilter().preview(changed));
        var text=new Source("one","v1","owner/tool",source.text()+"\n其他内容。\n");
        base.review(text,new ReadmeKnowledgeFilter().preview(text));assertEquals(4,calls.get());
        var different=engine("a","m1",new Limits(10,12000,1024,4,0,100000),t);
        assertNotEquals(base.plan(source,initial()).getFirst().cacheKey(),different.plan(source,initial()).getFirst().cacheKey());
    }
    @Test void unknownDuplicateMissingIdAndExtraFactFieldsRejectWholeBatch() throws Exception {
        for(String fault:List.of("unknown","duplicate","missing","extra","schema","enum")) {
            var result=engine(fault,"m1",limits(1,0),(s,u,o)->{
                var root=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(answer(u,"KEEP"));
                var rows=(com.fasterxml.jackson.databind.node.ArrayNode)root.path("selections");
                var row=(com.fasterxml.jackson.databind.node.ObjectNode)rows.get(0);
                switch(fault) {
                    case "unknown"->row.put("blockId","another-project");
                    case "duplicate"->rows.set(1,row.deepCopy());
                    case "missing"->rows.remove(0);
                    case "extra"->row.put("rewrittenText","invented capability");
                    case "schema"->root.put("schemaVersion","wrong");
                    case "enum"->row.put("scope","invented");
                }
                return new Reply(root.toString(),"stop",Map.of());
            }).review(source,initial());
            assertTrue(result.selections().isEmpty(),fault);assertEquals(initial().reviewBlockIds(),result.preview().reviewBlockIds(),fault);
            assertTrue(result.attempts().getFirst().status().startsWith("OUTPUT_REJECTED_"));
        }
    }
    @Test void incompleteFinishDuplicateJsonKeysAndTrailingOutputReject() throws Exception {
        for(String mode:List.of("length","trailing","duplicateKey")) {
            var r=engine(mode,"m",limits(1,0),(s,u,o)->new Reply(mode.equals("trailing")?answer(u,"KEEP")+" extra":
                    mode.equals("duplicateKey")?answer(u,"KEEP").replace("\"schemaVersion\":", "\"schemaVersion\":\"wrong\",\"schemaVersion\":"):answer(u,"KEEP"),
                    mode.equals("length")?"length":"stop",Map.of())).review(source,initial());
            assertTrue(r.selections().isEmpty());
        }
    }
    @Test void timeoutAndRetryConsumeAttemptsAndCannotResetOnRestart() throws Exception {
        AtomicInteger calls=new AtomicInteger();Transport t=(s,u,o)->{calls.incrementAndGet();throw new TimeoutException();};
        var first=engine("same","m",limits(2,1),t).review(source,initial());
        assertEquals("TIMEOUT",first.attempts().getFirst().status());assertEquals(2,calls.get());assertEquals(2,starts());
        var restarted=engine("same","m",limits(2,1),t).review(source,initial());
        assertEquals("BUDGET_EXHAUSTED",restarted.attempts().getFirst().status());assertEquals(2,calls.get());
    }
    @Test void tokenReservationBlocksBeforeTransportAndOfflinePlanningCallsNothing() throws Exception {
        Transport never=(s,u,o)->{fail("Must not call");return null;};
        assertFalse(engine("x","m",Limits.offline(),never).plan(source,initial()).isEmpty());
        engine("x","m",Limits.offline(),never).review(source,initial());assertEquals(0,starts());
        engine("y","m",new Limits(1,12000,2048,4,0,1),never).review(source,initial());assertEquals(0,starts());
    }
    @Test void hardReviewReasonsNeverGoToModel() throws Exception {
        Source hard=new Source("h","v","o/r","# Usage\n```js\nimport 'incomplete\n```\n\n<img src='feature.png' alt='inventory'/>\n\n100 free model calls\n");
        var p=new ReadmeKnowledgeFilter().preview(hard);
        var r=engine("hard","m",limits(1,0),(s,u,o)->{fail("Hard review must stay isolated");return null;}).review(hard,p);
        assertTrue(r.batches().isEmpty());assertTrue(r.selections().isEmpty());assertEquals(p.reviewBlockIds(),r.preview().reviewBlockIds());
    }
    @Test void protectedConditionCannotBeDropped() throws Exception {
        Source guarded=new Source("g","v","o/r","# Browser support\nA browser needs 4 GB memory.\n");
        var p=new ReadmeKnowledgeFilter().preview(guarded);
        var r=engine("guard","m",limits(1,0),(s,u,o)->new Reply(answer(u,"DROP"),"stop",Map.of())).review(guarded,p);
        assertFalse(r.batches().isEmpty());assertTrue(r.selections().isEmpty());assertEquals(p.reviewBlockIds(),r.preview().reviewBlockIds());
    }
    @Test void cacheCorruptionDoesNotBypassLimitsOrValidation() throws Exception {
        var first=engine("a","m",limits(1,0),(s,u,o)->valid(u)).review(source,initial());
        String key=first.batches().getFirst().cacheKey();Files.writeString(temp.resolve("cache").resolve(key+".json"),"{bad");
        var r=engine("a","m",limits(1,0),(s,u,o)->{fail("Budget already used");return null;}).review(source,initial());
        assertEquals("BUDGET_EXHAUSTED",r.attempts().getFirst().status());assertTrue(r.selections().isEmpty());
    }
    @Test void oversizedOriginalContextIsDeferredWithoutTruncation() throws Exception {
        Source longSource=new Source("l","v","o/r","# Warning\nRequires "+"x".repeat(18000)+"\n\n## Misc\n未知说明。\n");
        var p=new ReadmeKnowledgeFilter().preview(longSource);
        var r=engine("long","m",limits(1,0),(s,u,o)->{fail("Context too large");return null;}).review(longSource,p);
        assertTrue(r.batches().isEmpty());assertEquals(longSource.text(),r.preview().blocks().stream().map(Block::text).reduce("",String::concat));
    }
    @Test void concurrentReservationsNeverExceedSharedLimit() throws Exception {
        var journal=new ReadmeReviewJournal(temp.resolve("parallel.jsonl"),"r",true);
        try(var workers=Executors.newFixedThreadPool(4)) {
            List<Future<Boolean>> results=new ArrayList<>();for(int i=0;i<8;i++)results.add(workers.submit(()->journal.reserve("k","m",100,100,2,10000)));
            int granted=0;for(var r:results)if(r.get())granted++;assertEquals(2,granted);
        }
    }
    @Test void reassemblyRefusesChangingFixedRuleDecisionsOrRawText() {
        var p=initial();var list=new ArrayList<>(p.blocks());Block b=list.getFirst();
        list.set(0,new Block(b.blockId(),b.kind(),b.startUtf16(),b.endUtf16(),b.startLine(),b.endLine(),"invented",b.sha256(),b.sectionPath(),b.headingIds(),b.decision(),b.reason(),b.ruleIds(),b.knowledgeTypeHints()));
        assertThrows(IllegalArgumentException.class,()->new ReadmeKnowledgeFilter().reassemble(source,p,list));
    }
    @Test void droppedExplanationCannotLeaveItsCodeApproved() throws Exception {
        var s=new Source("c","v","o/r","# Tool\n某段需要一起理解的上下文。\n\n```sh\nrun --option\n```\n");
        var p=new ReadmeKnowledgeFilter().preview(s);
        var r=engine("pair","m",limits(1,0),(system,user,o)->{
            var root=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(answer(user,"KEEP"));
            var row=(com.fasterxml.jackson.databind.node.ObjectNode)root.path("selections").get(0);row.put("decision","DROP");row.put("reason","NOISE");
            return new Reply(root.toString(),"stop",Map.of());
        }).review(s,p);
        assertTrue(r.preview().blocks().stream().filter(b->b.kind().equals("CODE")).allMatch(b->b.decision()==Decision.REVIEW));
    }
    @Test void failureDiagnosticsDoNotLogProviderExceptionText() throws Exception {
        var r=engine("secret","m",limits(1,0),(s,u,o)->{throw new IllegalStateException("SENSITIVE_PROVIDER_TEXT");}).review(source,initial());
        assertEquals("PROVIDER_OR_STORAGE_FAILURE",r.attempts().getFirst().status());
        assertFalse(Files.readString(temp.resolve("ledger.jsonl")).contains("SENSITIVE_PROVIDER_TEXT"));
    }
    @Test void explicitBatchSelectionCannotExecuteOtherOrInventedBatches() throws Exception {
        Transport never=(s,u,o)->{fail("No selected batch");return null;};
        var e=engine("subset","m",limits(1,0),never);
        assertTrue(e.review(source,initial(),Set.of()).attempts().isEmpty());
        assertThrows(IllegalArgumentException.class,()->e.review(source,initial(),Set.of("foreign-batch")));
        assertEquals(0,starts());
    }
    @Test void mixedEditionBlockCannotBeAssignedEntirelyToCore() throws Exception {
        var source=new Source("mixed","v","o/r","# Tool\n社区版提供编辑器，企业功能请参阅另外的产品。\n");
        var p=new ReadmeKnowledgeFilter().preview(source);
        // Exercise the same review gate even if the deterministic rule already knows this wording.
        Block old=p.blocks().getLast();var list=new ArrayList<>(p.blocks());
        list.set(list.size()-1,new Block(old.blockId(),old.kind(),old.startUtf16(),old.endUtf16(),old.startLine(),old.endLine(),old.text(),old.sha256(),
                old.sectionPath(),old.headingIds(),Decision.REVIEW,"SEMANTIC_REVIEW_NEEDED",List.of("R10"),List.of("UNKNOWN")));
        p=new Preview(p.policyVersion(),p.parserVersion(),p.documentId(),p.versionId(),p.sourceRef(),p.sourceSha256(),p.repoRoleHint(),list,List.of(),List.of(),List.of(old.blockId()),List.of());
        var r=engine("mixed","m",limits(1,0),(s,u,o)->valid(u)).review(source,p);
        assertTrue(r.preview().knowledgeCandidates().isEmpty());
        assertEquals(Decision.REVIEW,r.recommendation().assessments().getLast().effectiveDecision());
    }
}
