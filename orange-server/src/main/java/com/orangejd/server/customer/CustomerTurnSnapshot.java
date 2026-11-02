package com.orangejd.server.customer;

import java.util.*;

/** Immutable checkpoint projection for graph persistence and diagnostics. */
final class CustomerTurnSnapshot {
        static Map<String,Object> capture(CustomerConversation.Turn turn,CustomerJson json) {
            Map<String,Object> out=new LinkedHashMap<>();out.put("runId",turn.id);out.put("sessionId",turn.sessionId);out.put("next",turn.next);
            out.put("graphVersion","customer-graph-v2");out.put("stateSchemaVersion",2);
            if(turn.state!=null)out.put("workflowTask",CustomerWorkflow.visible(turn.state));
            if(turn.intent!=null)out.put("intent",turn.intent);
            if(turn.state!=null)out.put("conditions",CustomerRecommendationAdvice.conditions(turn.state));
            if(turn.prior!=null)out.put("priorConditions",CustomerRecommendationAdvice.conditions(turn.prior));
            out.put("candidatePoolCount",turn.candidates.size());out.put("selectedCount",turn.cards.size());
            out.put("recalledChunkCount",turn.related.size());out.put("preparedEvidenceCount",turn.evidence.size());
            if(turn.state!=null)out.put("purposeStatus",Objects.toString(CustomerPurpose.status(turn.state),"NOT_REQUIRED"));
            if(turn.context!=null){out.put("toolCalls",turn.context.calls);out.put("knowledgeSearches",turn.context.searches);}
            out.put("candidateIdentities",turn.cards.stream().map(CustomerProjectFeedback::identity).toList());
            out.put("evidenceIds",turn.references.stream().map(r->Objects.toString(r.get("evidenceId"),"")).toList());
            if(turn.found!=null)out.put("generation",turn.found.generation());
            if(turn.result!=null)out.put("businessStatus",turn.result.getOrDefault("status","PENDING"));
            return json.read(json.write(out));
        }
}
