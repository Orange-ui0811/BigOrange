package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.service.PersonaPromptContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in real provider/B corpus acceptance. Synthetic owners; HTTP authentication is tested separately. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"orange.customer.enabled=true","orange.customer.all-authenticated-users=true","mybatis.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
@EnabledIfEnvironmentVariable(named="RUN_CUSTOMER_GRAPH_ACCEPTANCE",matches="true")
class CustomerGraphProviderAcceptanceTest {
    @MockBean CustomerRuns disabledStartupRecovery;
    @MockBean PersonaPromptContextService profiles;
    @MockBean com.orangejd.server.service.AiAuditService disabledAudit;
    @MockBean com.orangejd.server.task.LeaderboardRefreshTask disabledRefresh;
    @MockBean org.springframework.web.socket.server.standard.ServerEndpointExporter disabledWebSockets;
    @MockBean(name="org.springframework.context.annotation.internalScheduledAnnotationProcessor")
    org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor disabledSchedules;
    @Autowired CustomerStore store;@Autowired CustomerMapper db;@Autowired CustomerJson json;
    @Autowired CustomerConversation conversation;@Autowired CustomerExecutionTrace trace;
    @SpyBean CustomerModel model;
    @Autowired CustomerProperties config;
    @Autowired LegacyCustomerEngine legacy;@Autowired LangGraphCustomerEngine graph;
    @Test void frozenCasesTwoRoundsTwoEngines() throws Exception {
        when(profiles.confirmedContext(anyLong())).thenReturn("");
        // Runtime settings may override environment; enforce the explicitly authorized destination.
        // CustomerModel prefixes the external policy with the requested model name.
        var endpoint=java.net.URI.create(conversation.model.policyIdentity().split("\\|")[2]);
        assertThat(endpoint.getScheme()).isEqualTo("https");
        assertThat(endpoint.getHost()).isEqualToIgnoringCase("api.deepseek.com");
        Path root=Files.isDirectory(Path.of("docs/customer"))?Path.of("."):Path.of("..");
        Path folder=root.resolve(System.getenv().getOrDefault("CUSTOMER_GRAPH_REPORT_DIR","docs/customer/langgraph4j"));
        Files.createDirectories(folder);
        Path caseFile=root.resolve(System.getenv().getOrDefault("CUSTOMER_GRAPH_CASE_FILE","docs/customer/langgraph4j/acceptance-cases.json"));
        var cases=new ObjectMapper().readValue(caseFile.toFile(),new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>(){});
        boolean smoke="smoke".equals(System.getenv("CUSTOMER_GRAPH_PHASE"));
        boolean repair="repair".equals(System.getenv("CUSTOMER_GRAPH_PHASE"));
        int rounds=smoke?1:2;
        assertThat(cases).hasSize(repair?2:smoke?4:20);
        if("true".equals(System.getenv("CUSTOMER_GRAPH_ACTION_ONLY"))) {
            assertThat(cases).allMatch(c->!c.containsKey("turns") && !c.containsKey("prior"));
            Set<Object> allowed=new HashSet<>();cases.forEach(c->allowed.add(c.get("message")));
            var calls=new java.util.concurrent.atomic.AtomicInteger();
            doAnswer(invocation->{
                Object raw=invocation.getArgument(1);
                if(!CustomerState.PROMPT.equals(invocation.getArgument(0)) || !(raw instanceof Map<?,?> input)
                    || !allowed.contains(input.get("message")) || !"".equals(input.get("profile"))
                    || !"".equals(input.get("summary")) || !(input.get("history") instanceof List<?> history) || !history.isEmpty()
                    || calls.incrementAndGet()>8)throw new IllegalStateException("ACCEPTANCE_EGRESS_SCOPE_VIOLATION");
                return invocation.callRealMethod();
            }).when(model).call(anyString(),any(),any(),anyList());
        }
        var rows=new CopyOnWriteArrayList<Map<String,Object>>();var pool=Executors.newFixedThreadPool(2);
        // Real bounded scheduler; no startup recovery event interrupting another process's runs.
        var runs=new CustomerRuns(store,db,conversation,config,json,List.of(legacy,graph),trace);
        try {
            for(int round=1;round<=rounds;round++)for(var kind:round==1?List.of(CustomerExecutionEngine.Kind.LEGACY,CustomerExecutionEngine.Kind.LANGGRAPH4J):List.of(CustomerExecutionEngine.Kind.LANGGRAPH4J,CustomerExecutionEngine.Kind.LEGACY)) {
                config.setExecutionEngine(kind);final int cycle=round;
                for(int batch=0;batch<cases.size();batch+=2) {
                    List<Callable<Void>> work=new ArrayList<>();
                    for(int index=batch;index<Math.min(batch+2,cases.size());index++) {
                        var scenario=cases.get(index);final long owner=900001+index%2;
                        work.add(()->{rows.add(execute(runs,scenario,owner,kind,cycle));return null;});
                    }
                    for(var done:pool.invokeAll(work))done.get();
                    Files.writeString(folder.resolve("provider-acceptance.json"),json.write(Map.of("scope","Real CustomerRuns governance, configured model and B corpus; synthetic owners, no real account profile/history. HTTP authentication tested separately.","releaseApproved",false,"rows",rows)));
                }
            }
        } finally {runs.close();pool.shutdownNow();}
        // Automatic behavior checks are necessary, not sufficient: independent content review remains required.
        long legacyPass=rows.stream().filter(r->r.get("engine")==CustomerExecutionEngine.Kind.LEGACY && Boolean.TRUE.equals(r.get("passed"))).count();
        long graphPass=rows.stream().filter(r->r.get("engine")==CustomerExecutionEngine.Kind.LANGGRAPH4J && Boolean.TRUE.equals(r.get("passed"))).count();
        assertThat(graphPass).isGreaterThanOrEqualTo(smoke||repair?4:36).isGreaterThanOrEqualTo(legacyPass);
        if(repair)assertThat(legacyPass).isEqualTo(4);
        if(smoke) {
            assertThat(legacyPass).isEqualTo(4);
            assertThat(rows).allMatch(r->((Number)r.get("retrievalFailures")).intValue()==0);
            for(var kind:CustomerExecutionEngine.Kind.values())assertThat(rows.stream().filter(r->r.get("engine")==kind))
                .anyMatch(r->((Number)r.get("referenceCount")).intValue()>0);
        }
        assertThat(rows).allMatch(r->!Set.of("QUEUED","RUNNING").contains(r.get("runStatus")));
        assertThat(rows).allMatch(r->((Number)r.getOrDefault("criticalErrors",0)).intValue()==0);
    }
    Map<String,Object> execute(CustomerRuns runs,Map<String,Object> scenario,long owner,CustomerExecutionEngine.Kind kind,int round) throws Exception {
        long session=CustomerStore.number(store.create(owner,"Graph acceptance "+scenario.get("id")),"id");
        if(scenario.get("prior") instanceof Map<?,?> prior)db.state(session,json.write(prior));
        if(scenario.get("turns") instanceof List<?> turns) {
            var records=new ArrayList<Map<String,Object>>();boolean passed=true;long duration=0;
            for(Object raw:turns) {
                var turn=new LinkedHashMap<String,Object>();((Map<?,?>)raw).forEach((k,v)->turn.put(k.toString(),v));turn.put("id",scenario.get("id"));
                var record=executeTurn(runs,turn,session,owner,kind,round);records.add(record);
                passed &= Boolean.TRUE.equals(record.get("passed"));duration+=((Number)record.get("durationMs")).longValue();
            }
            var last=new LinkedHashMap<>(records.getLast());last.put("turns",records);last.put("passed",passed);last.put("durationMs",duration);
            last.put("criticalErrors",records.stream().mapToInt(r->((Number)r.getOrDefault("criticalErrors",0)).intValue()).sum());return last;
        }
        return executeTurn(runs,scenario,session,owner,kind,round);
    }
    Map<String,Object> executeTurn(CustomerRuns runs,Map<String,Object> scenario,long session,long owner,CustomerExecutionEngine.Kind kind,int round) throws Exception {
        var row=new LinkedHashMap<String,Object>();row.put("caseId",scenario.get("id"));row.put("engine",kind);row.put("round",round);
        var prior=json.read(db.session(session,owner).get("state_json"));
        long start=System.nanoTime();var submitted=runs.submit(session,owner,UUID.randomUUID().toString(),scenario.get("message").toString());
        String id=submitted.get("id").toString();row.put("runId",id);Map<String,Object> run;
        do {Thread.sleep(150);run=db.run(id);}while(CustomerStore.active(run) && System.nanoTime()-start<TimeUnit.SECONDS.toNanos(95));
        row.put("durationMs",(System.nanoTime()-start)/1_000_000);row.put("runStatus",run.get("status"));
        if(CustomerStore.active(run))runs.cancel(id,owner);
        var result=json.read(run.get("result_json"));var state=json.read(db.session(session,owner).get("state_json"));
        row.put("status",result.get("status"));row.put("intent",result.get("intent"));row.put("state",CustomerRecommendationAdvice.conditions(state));
        row.put("projectCount",((List<?>)result.getOrDefault("projects",List.of())).size());row.put("error",run.get("error_code"));
        row.put("usage",json.read(run.get("metrics_json")));row.put("passed","SUCCEEDED".equals(run.get("status")) && passes(scenario,new CustomerExecutionResult(result,state)));
        row.put("projects",result.getOrDefault("projects",List.of()));row.put("references",result.getOrDefault("references",List.of()));
        row.put("purposeProofs",state.getOrDefault(CustomerPurpose.STATE_KEY,Map.of()));
        int critical=0;
        for(Object raw:(List<?>)result.getOrDefault("projects",List.of())) {
            @SuppressWarnings("unchecked") var card=(Map<String,Object>)raw;
            // Display descriptions have been enriched; their hash is not the source-document hash.
            if(state.containsKey("topic") && (!(state.get(CustomerPurpose.STATE_KEY) instanceof Map<?,?> review)
                || !CustomerPurpose.scope(state).equals(review.get("scope")) || !(review.get("proofs") instanceof Map<?,?> proofs)
                || !proofs.containsKey(CustomerPurpose.key(card))))critical++;
            if(Boolean.TRUE.equals(card.get("importable")) && ("NEEDS_VERIFICATION".equals(card.get("matchStatus")) || ((List<?>)card.getOrDefault("references",List.of())).isEmpty()))critical++;
            for(Object reference:(List<?>)card.getOrDefault("references",List.of())) {
                var ref=(Map<?,?>)reference;if(!CustomerProjects.ownsEvidence(card,Objects.toString(ref.get("sourceRef"),"")))critical++;
            }
        }
        row.put("criticalErrors",critical);
        row.put("passed",Boolean.TRUE.equals(row.get("passed")) && projectPositionsMatch(scenario,result,prior));
        row.put("referenceCount",((List<?>)result.getOrDefault("references",List.of())).size());
        row.put("retrievalFailures",db.steps(id).stream().filter(s->Set.of("searchKnowledge","knowledge","recommendationRecall","projectEvidence").contains(s.get("tool_name")) && Set.of("FAILED","DEGRADED").contains(s.get("status"))).count());
        assertThat(run.get("execution_engine")).isEqualTo(kind.name());
        System.out.println("GRAPH_ACCEPTANCE "+kind+" "+scenario.get("id")+" passed="+row.get("passed"));return row;
    }
    boolean passes(Map<String,Object> scenario,CustomerExecutionResult output) {
        var result=output.result();var expected=(Map<?,?>)scenario.get("expected");
        if(expected.isEmpty())throw new IllegalArgumentException("EMPTY_ACCEPTANCE_CRITERIA");
        if(expected.get("intents") instanceof List<?> intents && !intents.contains(result.get("intent")))return false;
        if(expected.get("statuses") instanceof List<?> statuses && !statuses.contains(result.get("status")))return false;
        if(expected.get("state") instanceof Map<?,?> fields)for(var entry:fields.entrySet())if(!Objects.equals(output.state().get(entry.getKey()),entry.getValue()))return false;
        if(expected.get("goalIncludes") instanceof List<?> goals)for(Object goal:goals)if(!Objects.toString(output.state().get("goal"),"").contains(goal.toString()))return false;
        if(expected.get("topicIncludesAny") instanceof List<?> topics && topics.stream().noneMatch(topic->CustomerTopics.display(output.state().get("topic")).equalsIgnoreCase(topic.toString()) || CustomerTopics.display(output.state().get("topic")).contains(topic.toString())))return false;
        if(Boolean.TRUE.equals(expected.get("referencesRequired")) && ((List<?>)result.getOrDefault("references",List.of())).isEmpty())return false;
        if(expected.get("absent") instanceof String key && output.state().containsKey(key))return false;
        if(Boolean.TRUE.equals(expected.get("projectsRequired")) && ((List<?>)result.getOrDefault("projects",List.of())).isEmpty())return false;
        return !Set.of("FAILED","RELEVANCE_UNAVAILABLE","KNOWLEDGE_UNAVAILABLE").contains(Objects.toString(result.get("status"),""));
    }
    boolean projectPositionsMatch(Map<String,Object> scenario,Map<String,Object> result,Map<String,Object> prior) {
        var expected=(Map<?,?>)scenario.get("expected");
        if(!(expected.get("projectPositions") instanceof List<?> positions))return true;
        if(!(prior.get("lastCandidates") instanceof List<?> candidates))return false;
        var cards=(List<?>)result.getOrDefault("projects",List.of());if(cards.size()!=positions.size())return false;
        for(int index=0;index<positions.size();index++) {
            int pos=((Number)positions.get(index)).intValue();if(pos<1 || pos>candidates.size())return false;
            var wanted=(Map<?,?>)candidates.get(pos-1);var actual=(Map<?,?>)cards.get(index);
            if(!Objects.equals(wanted.get("type"),actual.get("type")) || !Objects.toString(wanted.get("id")).equals(Objects.toString(actual.get("id"))))return false;
        }
        return true;
    }
}
