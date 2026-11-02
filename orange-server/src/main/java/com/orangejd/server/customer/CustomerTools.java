package com.orangejd.server.customer;

import org.springframework.stereotype.Service;
import com.orangejd.server.ragb.service.RagBSearchService;
import com.orangejd.server.ragb.retrieval.RagBSearchConstraints;
import com.orangejd.server.service.PersonaPromptContextService;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.concurrent.*;

@Service
public class CustomerTools {
    private final RagBSearchService knowledge;
    private final CustomerProjects projects;
    private final PersonaPromptContextService profiles;
    private final CustomerMapper db;
    private final CustomerJson json;
    private final com.orangejd.server.ragb.mapper.RagBMapper indexes;
    private final CustomerProperties config;
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),r->{ Thread t=new Thread(r,"customer-tool"); t.setDaemon(true); return t; });
    public record ToolSpec(String permission,int timeoutSeconds,int maxResults,Set<String> requiredParameters) {}
    public static final Map<String,ToolSpec> SPECS=Map.of(
        "searchKnowledge",new ToolSpec("OWN_SESSION_READ",15,20,Set.of("query","sourceTypes")),
        "searchProjects",new ToolSpec("CATALOG_AND_DOCUMENT_READ",10,3,Set.of()),
        "getProjectDetail",new ToolSpec("CATALOG_AND_DOCUMENT_READ",5,1,Set.of("type","id","conditions")),
        "getConfirmedProfile",new ToolSpec("OWN_PROFILE_READ",5,1,Set.of()),
        "searchConversationHistory",new ToolSpec("OWN_SESSION_HISTORY_READ",3,5,Set.of("query")),
        "readConversationSource",new ToolSpec("OWN_SESSION_HISTORY_READ",3,1,Set.of("messageId")));
    public CustomerTools(RagBSearchService k,CustomerProjects p,PersonaPromptContextService profiles,CustomerMapper db,CustomerJson json,com.orangejd.server.ragb.mapper.RagBMapper indexes,CustomerProperties config) { knowledge=k; projects=p; this.profiles=profiles; this.db=db; this.json=json; this.indexes=indexes; this.config=config; }
    public static final Set<String> REGISTERED=Set.of("searchKnowledge","searchProjects","getProjectDetail","getConfirmedProfile","searchConversationHistory","readConversationSource");
    public class Context {
        final String run; final long owner; final Instant deadline; int calls; int searches;int historyCalls;
        final Map<String,Object> cache=new HashMap<>(); final Set<String> evidence=new HashSet<>();
        String generation;
        CustomerUsageLedger ledger;
        public void observe(List<Map<String,Object>> usage) {if(usage instanceof CustomerUsageLedger value)ledger=value;}
        public Context(String run,long owner,Instant deadline) { this.run=run; this.owner=owner; this.deadline=deadline; }
        @SuppressWarnings("unchecked")
        private <T> T execute(String tool,Object params,Supplier<T> work) {
            check(); String key=tool+":"+json.write(params); if(cache.containsKey(key)) return (T)cache.get(key);
            if(!REGISTERED.contains(tool))throw new IllegalStateException("TOOL_LIMIT");
            calls++;
            boolean historyTool=Set.of("searchConversationHistory","readConversationSource").contains(tool);
            if(historyTool?++historyCalls>2:calls-historyCalls>5)throw new IllegalStateException("TOOL_LIMIT");
            if(tool.equals("searchKnowledge") && ++searches>2) throw new IllegalStateException("SEARCH_LIMIT");
            Future<T> future=null;
            try {
                long remaining=java.time.Duration.between(Instant.now(),deadline).toMillis();
                if(remaining<=0) throw new IllegalStateException("DEADLINE_EXCEEDED");
                ToolSpec spec=SPECS.get(tool);
                if(!(params instanceof Map<?,?> values) || !values.keySet().containsAll(spec.requiredParameters())) throw new IllegalArgumentException("INVALID_TOOL_PARAMETERS");
                final String parentNode=ledger==null?null:ledger.nodeId;
                Instant toolBudget=Instant.now().plusSeconds(spec.timeoutSeconds());
                final Instant toolDeadline=deadline.isBefore(toolBudget)?deadline:toolBudget;
                future=executor.submit(()->{
                    long started=System.nanoTime();
                    try(var limit=com.orangejd.server.foundation.external.ExternalCallDeadline.open(toolDeadline);
                        var observation=com.orangejd.server.service.TrendLabObservation.open(stage->{if(ledger!=null)ledger.trace.timing(run,parentNode,stage);})) {
                        check();
                        if(!Instant.now().isBefore(toolDeadline))throw new IllegalStateException("TOOL_TIMEOUT");
                        T result=work.get();check();return result;
                    }
                    finally {if(ledger!=null)ledger.trace.payload(run,parentNode,"TOOL_EXECUTION",Map.of("tool",tool,"durationMs",(System.nanoTime()-started)/1_000_000,"parameters",params));}
                });
                T result=future.get(Math.max(1,java.time.Duration.between(Instant.now(),toolDeadline).toMillis()),TimeUnit.MILLISECONDS);
                if(ledger!=null)ledger.payload("TOOL_RESULT",Map.of("tool",tool,"result",result));
                check(); cache.put(key,result); db.step(run,tool,"SUCCEEDED",json.write(Map.of("parameters",params,"result",result))); return result;
            }
            catch(TimeoutException ex) { if(future!=null) future.cancel(true); db.step(run,tool,"FAILED",json.write(Map.of("errorType","TOOL_TIMEOUT"))); throw new IllegalStateException("TOOL_TIMEOUT"); }
            catch(InterruptedException ex) { if(future!=null) future.cancel(true); Thread.currentThread().interrupt(); throw new IllegalStateException("RUN_STOPPED"); }
            catch(ExecutionException ex) {
                if(ex.getCause() instanceof RuntimeException cause) {
                    db.step(run,tool,"FAILED",json.write(CustomerRecovery.details(cause)));throw cause;
                }
                db.step(run,tool,"FAILED",json.write(Map.of("errorCode","TOOL_FAILED","errorType",ex.getCause().getClass().getSimpleName())));
                throw new IllegalStateException("TOOL_FAILED");
            }
            catch(RejectedExecutionException ex) { db.step(run,tool,"FAILED",json.write(Map.of("errorType","TOOL_CAPACITY_EXCEEDED"))); throw new IllegalStateException("TOOL_CAPACITY_EXCEEDED",ex); }
            catch(RuntimeException ex) { db.step(run,tool,"FAILED",json.write(Map.of("errorType",ex.getClass().getSimpleName()))); throw ex; }
            finally { if(future instanceof Runnable task && future.isCancelled())executor.remove(task); }
        }
        public void check() {
            if(!config.canAccess(owner)) throw new IllegalStateException("CUSTOMER_DISABLED");
            if(Thread.currentThread().isInterrupted() || !CustomerStore.active(db.run(run))) throw new IllegalStateException("RUN_STOPPED");
            if(Instant.now().isAfter(deadline)) throw new IllegalStateException("DEADLINE_EXCEEDED");
        }
        public String profile() { return execute("getConfirmedProfile",Map.of(),()->profiles.confirmedContext(owner)); }
        public String contextRead(String tool,Map<String,Object> parameters,Supplier<String> operation) {
            if(!Set.of("searchConversationHistory","readConversationSource").contains(tool))throw new IllegalArgumentException("INVALID_CONTEXT_TOOL");
            return execute(tool,parameters,operation);
        }
        public List<Map<String,Object>> projects(Map<String,Object> state) { return execute("searchProjects",state,()->projects.search(state)); }
        public Map<String,Object> diagnose(Map<String,Object> state) { return execute("searchProjects",Map.of("purpose","COUNTERFACTUAL_COUNTS","conditions",state),()->projects.diagnose(state)); }
        public List<Map<String,Object>> rankedProjects(Map<String,Object> state,List<com.orangejd.server.ragb.retrieval.RagBSearchHit> hits) {
            return execute("searchProjects",Map.of("conditions",state,"semanticChunks",hits.stream().map(h->h.chunkId()).toList()),()->projects.search(state,hits));
        }
        public List<Map<String,Object>> purposeCandidates(Map<String,Object> state,List<com.orangejd.server.ragb.retrieval.RagBSearchHit> hits,String query) {
            return execute("searchProjects",Map.of("purpose","PURPOSE_REVIEW","conditions",state,"query",query),()->projects.purposeCandidates(projects.pool(),state,hits,query));
        }
        public Map<String,Object> detail(String type,long id,Map<String,Object> state) { return execute("getProjectDetail",Map.of("type",type,"id",id,"conditions",state),()->projects.exploreDetail(type,id,state)); }
        public RagBSearchService.SearchResult knowledge(String query,Set<String> types) {
            return search(query,types,Map.of(),()->knowledge.retrieve(query,8,0.55,RagBSearchConstraints.sources(types)));
        }
        public RagBSearchService.SearchResult projectRecall(String query) {
            Set<String> types=Set.of("BEGINNER_PROJECT","CAREER_PROJECT","GITHUB");
            return search(query,types,Map.of("purpose","PROJECT_RANKING","topK",20,"projectDiversity",true),()->knowledge.retrieve(query,20,0.55,RagBSearchConstraints.sources(types).projectDiversity()));
        }
        public RagBSearchService.SearchResult projectEvidence(String query,List<Map<String,Object>> cards) {
            Set<String> types=Set.of("BEGINNER_PROJECT","CAREER_PROJECT","GITHUB");
            List<Map<String,Object>> identities=cards.stream().map(c->Map.of("type",c.get("type"),"sourceRef",c.get("sourceRef"),"name",c.get("name"))).toList();
            return search(query,types,Map.of("projects",identities),()->{
                Set<Long> documents=new LinkedHashSet<>();
                for(var c:cards) documents.addAll(indexes.selectProjectEvidenceDocuments(c.get("type").toString(),c.get("sourceRef").toString(),c.get("name").toString()));
                if(documents.isEmpty()) {
                    var active=indexes.selectActiveGeneration();
                    return new RagBSearchService.SearchResult(active.getGenerationKey(),active.getAdapterType(),0,0,Map.of("PROJECT_DOCUMENTS_MISSING",cards.size()),List.of(),List.of(),"REFUSE_NO_MATCH");
                }
                return knowledge.retrieve(query,20,0.55,RagBSearchConstraints.sources(types).documents(documents));
            });
        }
        private RagBSearchService.SearchResult search(String query,Set<String> types,Map<String,Object> scope,Supplier<RagBSearchService.SearchResult> work) {
            check();
            if(query==null || query.isBlank() || query.length()>2000) throw new IllegalArgumentException("INVALID_QUERY");
            var active=indexes.selectActiveGeneration();
            if(active==null || !"bge-m3".equals(active.getEmbeddingModel()) || !Objects.equals(1024,active.getEmbeddingDimensions()) || !"STRUCTURE_M3_512_768_64_V1".equals(active.getChunkPolicy()) || !"HNSW_HYBRID".equals(active.getAdapterType())) throw new IllegalStateException("B_INDEX_POLICY_MISMATCH");
            Map<String,Object> params=new LinkedHashMap<>(scope); params.put("query",query); params.put("sourceTypes",types);
            RagBSearchService.SearchResult result=execute("searchKnowledge",params,work);
            if(!Objects.equals(active.getGenerationKey(),result.generation()) || generation!=null && !generation.equals(result.generation())) throw new IllegalStateException("INDEX_GENERATION_CHANGED");
            generation=result.generation();
            db.generation(run,result.generation());
            boolean added=false; for(var hit:result.hits()) if(evidence.add(Long.toString(hit.chunkId()))) added=true;
            if(!added && searches>1) db.step(run,"searchKnowledge","NO_NEW_EVIDENCE","{}");
            return result;
        }
    }
    public Context context(String run,long owner,Instant deadline) { return new Context(run,owner,deadline); }
    @jakarta.annotation.PreDestroy public void close() { executor.shutdownNow(); }
}
