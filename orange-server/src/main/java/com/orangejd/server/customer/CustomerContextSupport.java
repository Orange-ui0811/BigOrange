package com.orangejd.server.customer;

import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

/** Request-local wiring: source recovery cannot mutate tasks or authorize project actions. */
@Service
public class CustomerContextSupport {
    private final CustomerHistoryRecovery recovery;
    private final CustomerSummaryService summaries;
    private final CustomerMapper db;
    private final CustomerJson json;
    private final CustomerProperties config;
    public CustomerContextSupport(CustomerHistoryRecovery recovery,CustomerSummaryService summaries,CustomerMapper db,CustomerJson json,CustomerProperties config) {
        this.recovery=recovery;this.summaries=summaries;this.db=db;this.json=json;this.config=config;
    }
    static final class Memory {
        CustomerHistoryRecovery.Scope scope;
        CustomerHistoryRecovery.Session reader;
        List<Map<String,Object>> recent=List.of(),segments=List.of(),sources=List.of();
        CustomerContextPacket packet;
        boolean attempted;
    }
    public void prepare(CustomerConversation.Turn t) {
        if(!(t.messageId instanceof Number id)||id.longValue()<=0)return; // Never manufacture archive identities.
        var memory=new Memory();memory.scope=scope(t,t.prior,id.longValue());t.memory=memory;
        memory.reader=recovery.open(memory.scope);
        var recent=new ArrayList<Map<String,Object>>();
        for(var row:recovery.recent(memory.scope,80)) {
            var item=new LinkedHashMap<>(CustomerHistory.view(row,json));
            item.put("messageId",CustomerContextRepository.number(row,"id"));item.put("runId",Objects.toString(row.get("run_id"),""));
            item.put("taskId",Objects.toString(row.get("task_id"),""));item.put("sessionId",t.sessionId);recent.add(item);
        }
        memory.recent=List.copyOf(recent);memory.segments=summaries.summaries(memory.scope);
        note(t,"contextSnapshot",Map.of("watermark",memory.scope.watermark(),"taskId",memory.scope.taskId(),"requirementVersion",memory.scope.requirementVersion(),"recentMessages",recent.size(),"summarySegments",memory.segments.size()));
    }
    public void recover(CustomerConversation.Turn t) {
        if(t.memory==null||t.memory.attempted||!needsHistory(t.message,t.resolution))return;
        recover(t,t.message);
    }
    public boolean recoverRequested(CustomerConversation.Turn t,Map<String,Object> action) {
        if(t.memory==null||t.memory.attempted||!Boolean.TRUE.equals(action.get("needsHistory"))||!Instant.now().plusSeconds(15).isBefore(t.deadline))return false;
        String query=Objects.toString(action.get("historyQuery"),t.message);
        if(query.isBlank())query=t.message;
        recover(t,query);return true;
    }
    private void recover(CustomerConversation.Turn t,String requestedQuery) {
        t.memory.attempted=true;t.context.check();
        try {
            // Use the same registered callback path as model-initiated tools; identities live in the closure.
            String query=requestedQuery.substring(0,Math.min(300,requestedQuery.length()));
            boolean otherTasks=t.message.matches("(?s).*((恢复|继续|切回).{0,16}(之前|上次|原来)|(之前|上次).{0,16}任务).*");
            var searchArgs=Map.<String,Object>of("query",query);
            var output=json.read(t.context.contextRead("searchConversationHistory",searchArgs,()->t.memory.reader.callbacks(otherTasks)[0].call(json.write(searchArgs))));
            var sources=new ArrayList<>(CustomerWorkflow.rows(output.get("sources")));
            if(!sources.isEmpty()) {
                var first=sources.stream().filter(s->Boolean.TRUE.equals(s.get("partial"))).findFirst().orElse(null);
                if(first!=null) {
                    var args=new LinkedHashMap<String,Object>();args.put("messageId",first.get("messageId"));args.put("start",0);args.put("length",1200);args.put("contentHash",first.get("contentHash"));
                    var whole=json.read(t.context.contextRead("readConversationSource",args,()->t.memory.reader.callbacks()[1].call(json.write(args))));
                    for(int i=0;i<sources.size();i++)if(Objects.equals(sources.get(i).get("messageId"),whole.get("messageId")))sources.set(i,whole);
                }
            }
            t.memory.sources=List.copyOf(sources);
            note(t,"historyRecovery",Map.of("status",sources.isEmpty()?"NO_SOURCE":"SOURCE_FOUND","calls",t.memory.reader.calls(),"messageIds",sources.stream().map(s->s.get("messageId")).toList()));
        } catch(RuntimeException ex) {
            CustomerRecovery.requireRecoverable(ex);t.context.check();
            note(t,"historyRecovery",Map.of("status","UNAVAILABLE","errorCode",CustomerRecovery.code(ex)));
        }
    }
    public Map<String,Object> input(CustomerConversation.Turn t) {
        var resolution=Map.<String,Object>of("positions",t.resolution.positions(),"references",t.resolution.references(),"ambiguous",t.resolution.ambiguous());
        if(t.memory==null)return CustomerDialogue.input(t.message,t.prior,t.profile,t.history.messages(),t.history.summary(),config.getInputTokens(),json,resolution);
        var memory=t.memory;
        memory.packet=CustomerContextAssembler.understanding(t.message,t.prior,t.profile,memory.recent,"",config.getInputTokens(),json,resolution,
            CustomerContextPacket.Scope.from(t.sessionId,t.prior,memory.scope.watermark()),memory.sources,memory.segments);
        note(t,"contextManifest",memory.packet.manifest());
        if(t.usage instanceof CustomerUsageLedger ledger)ledger.payload("CONTEXT_PACKET_MANIFEST",memory.packet.manifest());
        return memory.packet.requireInput();
    }
    public void bind(CustomerConversation.Turn t) {
        if(t.memory==null)return;
        t.memory.scope=scope(t,t.state,t.memory.scope.watermark());recovery.bind(t.memory.scope);
    }
    public void archive(CustomerConversation.Turn t) {
        if(t.memory==null)return;
        if(!Instant.now().plusSeconds(10).isBefore(t.deadline)||!com.orangejd.server.foundation.external.AiAcceptanceBudget.hasCapacity()) {
            note(t,"contextSummary",Map.of("status","SKIPPED_BUDGET"));return;
        }
        try {
            t.context.check();var outcome=summaries.maybeSummarize(t.memory.scope,t.deadline.minusSeconds(2),t.usage);
            note(t,"contextSummary",outcome);
        } catch(RuntimeException ex) {
            // Optional compression never replaces an already validated answer. Cancellation still stops commit.
            if(Set.of("RUN_STOPPED","CUSTOMER_DISABLED").contains(CustomerRecovery.code(ex))||Thread.currentThread().isInterrupted())throw ex;
            note(t,"contextSummary",Map.of("status","DEFERRED","errorCode",CustomerRecovery.code(ex)));
        }
    }
    static boolean needsHistory(String message,CustomerReferenceResolver.Resolution resolution) {
        return resolution.ambiguous()||message.matches("(?s).*(之前|上次|上一批|前面|最早|还记得|说过|原来那个|恢复.{0,12}任务).*");
    }
    private static CustomerHistoryRecovery.Scope scope(CustomerConversation.Turn t,Map<String,Object> state,long watermark) {
        var task=CustomerWorkflow.task(state);
        return new CustomerHistoryRecovery.Scope(t.owner,t.sessionId,t.id,Objects.toString(task.get("id"),""),CustomerWorkflow.number(task,"requirementVersion"),watermark);
    }
    private void note(CustomerConversation.Turn t,String name,Object metadata) {db.step(t.id,name,"RECORDED",json.write(metadata));}
}
