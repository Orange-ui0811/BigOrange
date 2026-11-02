package com.orangejd.server.ragb.ingest;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;
import static com.orangejd.server.ragb.ingest.ReadmeRecommendationProjection.Applicability;

/** Model selects existing blocks only. Invalid responses fail closed, and never rewrite original content. */
public final class ReadmeModelReview {
    public static final String POLICY="README_MODEL_REVIEW_V1_6", PROMPT="README_SELECTOR_ZH_V5_SOURCE_ADMISSION", SCHEMA="README_SELECTIONS_V2";
    private static final ObjectMapper JSON=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public enum Kind { PURPOSE, TECHNICAL_TEXT, USAGE, ARCHITECTURE, LICENSE, LIMITATION, MAINTENANCE, SOURCE_POINTER, OTHER }
    public enum Scope { CORE, RELATED_PRODUCT, ENTERPRISE, COMMUNITY, VERSION_SPECIFIC, PLATFORM_SPECIFIC, NON_PRODUCTION, UNKNOWN }
    public enum Reason { KNOWLEDGE, POINTER, NOISE, INSUFFICIENT, CONFLICT, UNCERTAIN }
    public record Selection(String blockId, Decision decision, Kind kind, Scope scope, Reason reason,
                            Applicability applicability, List<String> evidenceBlockIds) {
        public Selection { evidenceBlockIds=List.copyOf(evidenceBlockIds); }
    }
    public record Reply(String text, String finishReason, Map<String,Integer> usage) {
        public Reply { usage=usage==null?Map.of():Map.copyOf(usage); }
    }
    @FunctionalInterface public interface Transport { Reply call(String system,String user,int outputTokens) throws Exception; }
    public record Limits(int maxRequests,int maxInputUpperBound,int maxOutputTokens,int maxBlocks,int retries,long maxReservedTokens) {
        public Limits {
            if(maxRequests<0||maxRequests>40||maxInputUpperBound<1024||maxInputUpperBound>16000||maxOutputTokens<256||maxOutputTokens>4096
                    ||maxBlocks<1||maxBlocks>8||retries<0||retries>1||maxReservedTokens<0)throw new IllegalArgumentException("Invalid review limits");
        }
        public static Limits offline() {return new Limits(0,12000,2048,4,0,0);}
    }
    public record Batch(String cacheKey,String documentId,String versionId,String sourceRef,String sourceSha256,
                        List<String> targetIds,List<String> contextIds,String system,String user,int inputUpperBound) {}
    public record Attempt(String cacheKey,String status,boolean cached,List<String> targets) {}
    public record Result(Preview preview,List<Batch> batches,List<Attempt> attempts,List<Selection> selections,
                         List<String> deferredBlockIds, ReadmeRecommendationProjection.Projection recommendation) {}
    private static final Set<String> SOFT=Set.of("SEMANTIC_REVIEW_NEEDED","DEPENDENCY_NOT_APPROVED","STRUCTURE_REQUIRES_SEMANTIC_REVIEW");
    private final String modelIdentity;
    private final Limits limits;
    private final Path cache;
    private final ReadmeReviewJournal journal;
    private final Transport transport;
    public ReadmeModelReview(String modelIdentity,Limits limits,Path cache,ReadmeReviewJournal journal,Transport transport) {
        this.modelIdentity=Objects.requireNonNull(modelIdentity);this.limits=limits;this.cache=cache.toAbsolutePath().normalize();
        this.journal=Objects.requireNonNull(journal);this.transport=Objects.requireNonNull(transport);
        if(modelIdentity.isBlank())throw new IllegalArgumentException("Missing model identity");
    }
    public List<Batch> plan(Source source,Preview preview) throws Exception {
        verify(source,preview);
        List<Batch> batches=new ArrayList<>();List<Block> targets=new ArrayList<>();
        for(List<Block> unit:reviewUnits(preview)) {
            // A dependency unit is indivisible; exceeding either limit defers the whole unit.
            Batch single=batch(preview,unit);
            if(unit.size()>limits.maxBlocks()||single.inputUpperBound()>limits.maxInputUpperBound())continue;
            List<Block> next=new ArrayList<>(targets);next.addAll(unit);
            Batch trial=batch(preview,next);
            if(next.size()>limits.maxBlocks()||trial.inputUpperBound()>limits.maxInputUpperBound()) {
                if(!targets.isEmpty())batches.add(batch(preview,targets));targets.clear();
                targets.addAll(unit);
            } else targets.addAll(unit);
        }
        if(!targets.isEmpty())batches.add(batch(preview,targets));return List.copyOf(batches);
    }
    public Result review(Source source,Preview initial) throws Exception {
        return review(source,initial,null);
    }
    /** Acceptance/resume entry: execute only explicitly selected batches from this exact source plan. */
    public Result review(Source source,Preview initial,Set<String> selectedBatchKeys) throws Exception {
        List<Batch> batches=plan(source,initial);
        if(selectedBatchKeys!=null) {
            Set<String> known=new HashSet<>();batches.forEach(b->known.add(b.cacheKey()));
            if(!known.containsAll(selectedBatchKeys))throw new IllegalArgumentException("Unknown selected batch");
            batches=batches.stream().filter(b->selectedBatchKeys.contains(b.cacheKey())).toList();
        }
        Map<String,Block> original=map(initial);
        List<Attempt> attempts=new ArrayList<>();List<Selection> choices=new ArrayList<>();
        for(Batch batch:batches) {
            List<Selection> accepted=null;String status="NOT_RUN";boolean cached=false;
            Path file=cache.resolve(batch.cacheKey()+".json");
            if(Files.exists(file)) {
                try {
                    if(Files.size(file)>131072)throw new IllegalArgumentException("Cache too large");
                    JsonNode node=JSON.readTree(Files.readString(file));
                    if(!batch.cacheKey().equals(node.path("cacheKey").asText())||!modelIdentity.equals(node.path("modelIdentity").asText())
                            ||!sha256(node.path("response").asText()).equals(node.path("responseSha256").asText()))throw new IllegalArgumentException("Bad cache identity");
                    accepted=validate(batch,new Reply(node.path("response").asText(),"stop",Map.of()),original);
                    cached=true;status="CACHE_HIT";
                } catch(Exception ex) {status="CACHE_REJECTED";}
            }
            if(accepted==null)for(int retry=0;retry<=limits.retries();retry++) {
                if(!journal.reserve(batch.cacheKey(),modelIdentity,batch.inputUpperBound(),limits.maxOutputTokens(),limits.maxRequests(),limits.maxReservedTokens())) {
                    status="BUDGET_EXHAUSTED";break;
                }
                Reply reply=null;
                try {
                    reply=transport.call(batch.system(),batch.user(),limits.maxOutputTokens());
                    accepted=validate(batch,reply,original);
                    journal.outcome(batch.cacheKey(),"VALID_RESPONSE",safeUsage(reply.usage()));
                    writeCache(file,batch,reply);
                    status="MODEL_VALIDATED";break;
                } catch(Exception ex) {
                    if(ex instanceof InterruptedException)Thread.currentThread().interrupt();
                    status=failureStatus(ex,reply!=null);
                    journal.outcome(batch.cacheKey(),status,reply==null?Map.of():safeUsage(reply.usage()));
                    accepted=null;if(Thread.currentThread().isInterrupted())break;
                }
            }
            if(accepted!=null)choices.addAll(accepted);
            attempts.add(new Attempt(batch.cacheKey(),status,cached,batch.targetIds()));
        }
        var admitted=assembleSelections(source,initial,choices,modelIdentity,journal.runId());
        return new Result(admitted.preview(),batches,List.copyOf(attempts),List.copyOf(choices),admitted.preview().reviewBlockIds(),admitted.projection());
    }
    /** Shared deterministic merge. Caller must supply already protocol-validated selections, never raw provider JSON. */
    static ReadmeRecommendationProjection.Output assembleSelections(Source source,Preview initial,List<Selection> choices,String modelIdentity,String runId) {
        Map<String,Selection> decisions=new LinkedHashMap<>();
        for(Selection s:choices)if(decisions.put(s.blockId(),s)!=null)throw new IllegalArgumentException("Duplicate selection");
        // The shared structural graph below retains dependencies even when a model drops a parent.
        List<Block> reviewed=new ArrayList<>();
        for(Block b:initial.blocks()) {
            Selection s=decisions.get(b.blockId());
            if(b.decision()!=Decision.REVIEW||!eligible(b)||s==null||s.decision()==Decision.REVIEW&&s.reason()!=Reason.CONFLICT)reviewed.add(b);
            else if(s.decision()==Decision.REVIEW)reviewed.add(new Block(b.blockId(),b.kind(),b.startUtf16(),b.endUtf16(),b.startLine(),b.endLine(),b.text(),b.sha256(),
                    b.sectionPath(),b.headingIds(),Decision.REVIEW,"MODEL_CONFLICT",List.of("R10",POLICY),b.knowledgeTypeHints()));
            else reviewed.add(new Block(b.blockId(),b.kind(),b.startUtf16(),b.endUtf16(),b.startLine(),b.endLine(),b.text(),b.sha256(),
                    b.sectionPath(),b.headingIds(),s.decision(),"MODEL_"+s.reason(),List.of("R10",POLICY),List.of(s.kind().name())));
        }
        Preview result=new ReadmeKnowledgeFilter().reassemble(source,initial,reviewed);
        return new ReadmeRecommendationProjection().project(source,result,choices,modelIdentity,runId);
    }
    public static boolean eligible(Block b) {
        return b.decision()==Decision.REVIEW&&SOFT.contains(b.reason())&&!b.kind().equals("HEADING");
    }
    private List<List<Block>> reviewUnits(Preview preview) {
        Map<String,Block> all=map(preview);
        var rules=ReadmeRecommendationProjection.ruleAssessments(preview);
        Map<String,List<String>> edges=new LinkedHashMap<>();Map<String,List<String>> reversed=new LinkedHashMap<>();
        all.keySet().forEach(id->reversed.put(id,new ArrayList<>()));
        ReadmeKnowledgeFilter.dependencies(preview.blocks()).forEach((id,refs)->{
            var dependencies=refs.stream().filter(ref->!all.get(ref).kind().equals("HEADING")).toList();
            edges.put(id,dependencies);dependencies.forEach(ref->reversed.get(ref).add(id));
        });
        // Strong components are indivisible (paired commands/restores). A shared one-way prerequisite
        // remains a separate classification unit; final assembly still checks every dependency.
        record Frame(String id,boolean expanded){}
        Set<String> seen=new HashSet<>();List<String> finished=new ArrayList<>();
        for(String id:all.keySet())if(!seen.contains(id)){
            ArrayDeque<Frame> pending=new ArrayDeque<>();pending.push(new Frame(id,false));
            while(!pending.isEmpty()){
                var frame=pending.pop();
                if(frame.expanded()){finished.add(frame.id());continue;}
                if(!seen.add(frame.id()))continue;
                pending.push(new Frame(frame.id(),true));
                for(String ref:edges.get(frame.id()))if(!seen.contains(ref))pending.push(new Frame(ref,false));
            }
        }
        seen.clear();List<Set<String>> components=new ArrayList<>();
        for(int i=finished.size()-1;i>=0;i--){String id=finished.get(i);if(seen.contains(id))continue;
            Set<String> members=new LinkedHashSet<>();ArrayDeque<String> pending=new ArrayDeque<>();pending.push(id);
            while(!pending.isEmpty()){String next=pending.pop();if(!seen.add(next))continue;members.add(next);pending.addAll(reversed.get(next));}
            components.add(members);
        }
        Collections.reverse(components); // Prerequisites precede their dependents, without recursive traversal.
        List<List<Block>> units=new ArrayList<>();
        for(Set<String> component:components){
            List<Block> targets=preview.blocks().stream().filter(b->component.contains(b.blockId())&&ReadmeRecommendationProjection.needsModel(b,rules)).toList();
            if(targets.isEmpty())continue;
            Set<String> reachable=new HashSet<>();ArrayDeque<String> pending=new ArrayDeque<>(component);
            while(!pending.isEmpty()){String next=pending.pop();if(reachable.add(next))pending.addAll(edges.get(next));}
            // Only genuine prerequisites can propagate a hard review; unrelated siblings cannot.
            if(reachable.stream().map(all::get).anyMatch(b->b.decision()==Decision.REVIEW&&!eligible(b)))continue;
            units.add(targets);
        }
        return List.copyOf(units);
    }
    private Batch batch(Preview preview,List<Block> targets) throws Exception {
        LinkedHashSet<String> context=new LinkedHashSet<>();List<Block> all=preview.blocks();
        Map<String,List<String>> dependencies=ReadmeKnowledgeFilter.dependencies(all);
        for(Block anchor:ReadmeRecommendationProjection.anchorContext(preview))context.add(anchor.blockId());
        for(Block b:targets) {
            ReadmeKnowledgeFilter.addDependencies(b.blockId(),dependencies,context);
            context.addAll(b.headingIds());int at=all.indexOf(b);
            for(int direction:List.of(-1,1))for(int i=at+direction;i>=0&&i<all.size();i+=direction) {
                Block neighbor=all.get(i);if(neighbor.kind().equals("SPACE"))continue;
                if(neighbor.sectionPath().equals(b.sectionPath()))context.add(neighbor.blockId());break;
            }
        }
        // Existing explicit constraints remain model-visible. Oversized context defers the batch; never truncates guards.
        for(Block b:all)if(targets.stream().anyMatch(target->ReadmeSourceSemantics.applies(b,target))) {
            context.add(b.blockId());context.addAll(b.headingIds());
        }
        List<String> ids=targets.stream().map(Block::blockId).toList();context.removeAll(ids);
        Map<String,Block> blocks=map(preview);Map<String,Object> input=new LinkedHashMap<>();
        input.put("source",Map.of("documentId",preview.documentId(),"versionId",preview.versionId(),"sourceRef",preview.sourceRef(),"sha256",preview.sourceSha256()));
        input.put("targets",targets.stream().map(this::view).toList());input.put("context",context.stream().map(id->view(blocks.get(id))).toList());
        Map<String,List<String>> targetDependencies=new LinkedHashMap<>();
        for(Block b:targets)targetDependencies.put(b.blockId(),dependencies.get(b.blockId()));
        input.put("targetDependencies",targetDependencies);
        String user=JSON.writeValueAsString(input),system=systemPrompt();
        int upper=system.getBytes(StandardCharsets.UTF_8).length+user.getBytes(StandardCharsets.UTF_8).length+256;
        String key=sha256(POLICY+"\n"+PROMPT+"\n"+SCHEMA+"\n"+ReadmeRecommendationProjection.POLICY+"\n"+preview.policyVersion()+"\n"+preview.parserVersion()+"\n"+modelIdentity+"\n"+limits.maxOutputTokens()+"\n"+system+"\n"+user);
        return new Batch(key,preview.documentId(),preview.versionId(),preview.sourceRef(),preview.sourceSha256(),ids,List.copyOf(context),system,user,upper);
    }
    private Map<String,Object> view(Block b) {
        return Map.of("blockId",b.blockId(),"raw",b.text(),"kind",b.kind(),"sectionPath",b.sectionPath(),"ruleDecision",b.decision(),"ruleReason",b.reason());
    }
    public static String systemPrompt() { return ReadmeSelectionPrompt.systemPrompt(); }
    private List<Selection> validate(Batch batch,Reply reply,Map<String,Block> blocks) throws Exception {
        if(reply==null||!"stop".equals(reply.finishReason())||reply.text()==null||reply.text().length()>65536)throw new IllegalArgumentException("Incomplete response");
        JsonNode root=JSON.readTree(reply.text());exact(root,Set.of("schemaVersion","selections"));
        if(!SCHEMA.equals(root.path("schemaVersion").asText())||!root.path("selections").isArray()
                ||root.path("selections").size()!=batch.targetIds().size())throw new IllegalArgumentException("Selection count/schema mismatch");
        Set<String> unseen=new HashSet<>(batch.targetIds());List<Selection> result=new ArrayList<>();
        for(JsonNode node:root.path("selections")) {
            exact(node,Set.of("blockId","decision","kind","scope","reason","applicability","evidenceBlockIds"));
            for(String key:List.of("blockId","decision","kind","scope","reason","applicability"))if(!node.path(key).isTextual())throw new IllegalArgumentException("Wrong value type");
            if(!node.path("evidenceBlockIds").isArray())throw new IllegalArgumentException("Invalid selection evidence");
            List<String> evidence=new ArrayList<>();for(JsonNode id:node.path("evidenceBlockIds")) {
                if(!id.isTextual()||evidence.contains(id.asText()))throw new IllegalArgumentException("Invalid selection evidence");evidence.add(id.asText());
            }
            Set<String> allowed=new HashSet<>(batch.targetIds());allowed.addAll(batch.contextIds());
            if(evidence.isEmpty()||evidence.size()>allowed.size()||!allowed.containsAll(evidence)||!evidence.contains(node.path("blockId").asText()))throw new IllegalArgumentException("Invalid selection evidence");
            Selection s=new Selection(node.path("blockId").asText(),Decision.valueOf(node.path("decision").asText()),Kind.valueOf(node.path("kind").asText()),
                    Scope.valueOf(node.path("scope").asText()),Reason.valueOf(node.path("reason").asText()),Applicability.valueOf(node.path("applicability").asText()),evidence);
            if(!unseen.remove(s.blockId()))throw new IllegalArgumentException("Unknown or duplicate block");
            if(blocks.get(s.blockId()).decision()==Decision.KEEP&&s.decision()==Decision.DROP)throw new IllegalArgumentException("Protected DROP conflict");
            if(s.decision()==Decision.KEEP&&(s.kind()==Kind.OTHER||s.kind()!=Kind.SOURCE_POINTER&&s.applicability()!=Applicability.OUT_OF_SCOPE&&(s.scope()==Scope.UNKNOWN||s.applicability()!=Applicability.IN_SCOPE)
                    ||s.reason()!=(s.kind()==Kind.SOURCE_POINTER?Reason.POINTER:Reason.KNOWLEDGE)))throw new IllegalArgumentException("Uncertain KEEP");
            if(s.decision()==Decision.DROP&&(s.reason()!=Reason.NOISE||ReadmeSourceSemantics.condition(blocks.get(s.blockId()))))
                throw new IllegalArgumentException("Protected DROP conflict");
            result.add(s);
        }
        if(!unseen.isEmpty())throw new IllegalArgumentException("Missing block");return List.copyOf(result);
    }
    private static void exact(JsonNode node,Set<String> expected) {
        if(node==null||!node.isObject())throw new IllegalArgumentException("Expected object");
        Set<String> actual=new HashSet<>();node.fieldNames().forEachRemaining(actual::add);
        if(!actual.equals(expected))throw new IllegalArgumentException("Unknown/missing fields");
    }
    private Map<String,Integer> safeUsage(Map<String,Integer> usage) {
        Map<String,Integer> result=new LinkedHashMap<>();for(String key:List.of("prompt_tokens","completion_tokens","total_tokens"))
            if(usage.get(key)!=null&&usage.get(key)>=0)result.put(key,usage.get(key));return result;
    }
    private static String failureStatus(Exception error,boolean hasReply) {
        for(Throwable cause=error;cause!=null;cause=cause.getCause())
            if(cause instanceof java.net.SocketTimeoutException||cause instanceof java.util.concurrent.TimeoutException)return "TIMEOUT";
        if(!hasReply)return "PROVIDER_OR_STORAGE_FAILURE";
        String message=Objects.toString(error.getMessage(),"");
        // Only fixed diagnostic codes reach the journal; never log arbitrary provider text or enum values.
        String code=switch(message) {
            case "Incomplete response"->"INCOMPLETE_RESPONSE";
            case "Selection count/schema mismatch"->"COUNT_OR_SCHEMA";
            case "Unknown or duplicate block"->"BLOCK_ID";
            case "Unknown/missing fields","Expected object","Wrong value type"->"FIELDS";
            case "Uncertain KEEP"->"UNCERTAIN_KEEP";
            case "Protected DROP conflict"->"PROTECTED_DROP";
            case "Invalid selection evidence"->"EVIDENCE";
            case "Missing block"->"MISSING_BLOCK";
            default->error instanceof com.fasterxml.jackson.core.JsonProcessingException?"JSON_SYNTAX_OR_DUPLICATE_KEY":"ENUM_OR_OTHER";
        };
        return "OUTPUT_REJECTED_"+code;
    }
    private void writeCache(Path file,Batch batch,Reply reply) throws Exception {
        Files.createDirectories(cache);String text=JSON.writeValueAsString(Map.of("cacheKey",batch.cacheKey(),"modelIdentity",modelIdentity,
                "response",reply.text(),"responseSha256",sha256(reply.text())));
        Path temp=Files.createTempFile(cache,"review-",".tmp");
        try {Files.writeString(temp,text,StandardCharsets.UTF_8);
            try {Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}
        finally {Files.deleteIfExists(temp);}
    }
    private static Map<String,Block> map(Preview preview) {Map<String,Block> result=new LinkedHashMap<>();preview.blocks().forEach(b->result.put(b.blockId(),b));return result;}
}
