package com.orangejd.server.customer;

import org.bsc.langgraph4j.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public final class LangGraphCustomerEngine implements CustomerExecutionEngine {
    public static final List<String> NODES=List.of("load_context","resolve_references","understand_turn","reconcile_conditions","direct_response","diagnose","retrieve_knowledge","load_project_details","plan_recommendation","recall_candidates","review_purpose","select_projects","retrieve_project_evidence","prepare_evidence","generate_claims","validate_citations","verify_claims","assemble_project_cards","match_requirements","generate_advice","finalize_response","validate_response");
    private final CustomerConversation conversation;private final CustomerGraphRepository repository;private final CustomerJson json;private final CustomerExecutionTrace trace;
    public LangGraphCustomerEngine(CustomerConversation conversation,CustomerGraphRepository repository,CustomerJson json,CustomerExecutionTrace trace){this.conversation=conversation;this.repository=repository;this.json=json;this.trace=trace;}
    public Kind kind(){return Kind.LANGGRAPH4J;}
    public CustomerExecutionResult execute(CustomerRunContext context) {
        var turn=conversation.newTurn(context);
        try {
            turn.check();repository.begin(context.runId());
            var ledger=context.usage() instanceof CustomerUsageLedger value?value:null;
            var saver=new CustomerMysqlCheckpointSaver(context.runId(),repository,json,ledger);
            var runnable=RunnableConfig.builder().threadId("customer:"+context.runId()).build();
            // Persist before any tool/model use, even if framework START behavior changes.
            saver.put(runnable,org.bsc.langgraph4j.checkpoint.Checkpoint.builder().id(UUID.randomUUID().toString()).nodeId("__start__").nextNodeId("load_context").state(turn.snapshot()).build());
            long graphStarted=System.nanoTime();
            var graph=new StateGraph<CustomerGraphState>(new CustomerGraphSerializer(json));
            Map<String,String> routes=new LinkedHashMap<>();for(String name:NODES)routes.put(name,name);routes.put("END",StateGraph.END);
            for(String name:NODES) {
                // Completed futures execute synchronously on the bounded CustomerRuns worker.
                graph.addNode(name,(org.bsc.langgraph4j.action.AsyncNodeAction<CustomerGraphState>) state->{
                    trace.node(turn,kind().name(),name);
                    return CompletableFuture.completedFuture(turn.snapshot());
                });
                graph.addConditionalEdges(name,(org.bsc.langgraph4j.action.AsyncEdgeAction<CustomerGraphState>) state->CompletableFuture.completedFuture(state.next()),routes);
            }
            graph.addEdge(StateGraph.START,"load_context");
            var compiled=graph.compile(CompileConfig.builder().checkpointSaver(saver).releaseThread(false).recursionLimit(32).build());
            if(ledger!=null)ledger.timing("graph_create_compile",System.nanoTime()-graphStarted);
            compiled.invoke(turn.snapshot(),runnable).orElseThrow(()->new IllegalStateException("GRAPH_INCOMPLETE"));
            turn.check();return turn.output();
        } catch(RuntimeException ex){trace.archive(context.runId(),"FAILED",CustomerRecovery.code(ex));throw ex;}
        catch(Exception ex){trace.archive(context.runId(),"FAILED","GRAPH_EXECUTION_FAILED");throw new IllegalStateException("GRAPH_EXECUTION_FAILED",ex);}
    }
}
