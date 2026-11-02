package com.orangejd.server.customer;

import java.util.*;

/** Immutable model view plus a metadata-only manifest; it never replaces persisted business state. */
public record CustomerContextPacket(String stage,Scope scope,Map<String,Object> input,
        CustomerContextBudget.Check budget,List<String> sourceRefs,List<String> summaryRefs,
        List<String> omissions,boolean blocked,String reason) {
    public static final String POLICY_VERSION="customer-context-v1";
    public record Scope(long sessionId,String taskId,long requirementVersion,long watermark) {
        public Scope {taskId=Objects.requireNonNullElse(taskId,"");if(sessionId<0||requirementVersion<0||watermark<0)throw new IllegalArgumentException("INVALID_CONTEXT_SCOPE");}
        public static Scope from(long sessionId,Map<String,Object> state,long watermark){return new Scope(sessionId,Objects.toString(CustomerWorkflow.task(state).get("id"),""),CustomerWorkflow.number(CustomerWorkflow.task(state),"requirementVersion"),watermark);}
    }
    public CustomerContextPacket {
        Objects.requireNonNull(stage);Objects.requireNonNull(scope);Objects.requireNonNull(budget);
        input=freezeMap(input);sourceRefs=List.copyOf(sourceRefs);summaryRefs=List.copyOf(summaryRefs);omissions=List.copyOf(omissions);
        reason=Objects.requireNonNullElse(reason,"");
    }
    public Map<String,Object> requireInput(){if(blocked)throw new IllegalArgumentException(reason.isBlank()?"CONTEXT_BUDGET_EXCEEDED":reason);return input;}
    /** IDs and accounting only. Do not put model text, quotes or full task state in ordinary diagnostics. */
    public Map<String,Object> manifest(){
        var out=new LinkedHashMap<String,Object>(budget.manifest());out.put("policyVersion",POLICY_VERSION);
        out.put("sessionId",scope.sessionId());out.put("taskId",scope.taskId());out.put("requirementVersion",scope.requirementVersion());out.put("watermark",scope.watermark());
        out.put("sourceRefs",sourceRefs);out.put("summaryRefs",summaryRefs);out.put("omissions",omissions);out.put("blocked",blocked);out.put("reason",reason);
        return Collections.unmodifiableMap(out);
    }
    @SuppressWarnings("unchecked") static <T> T freeze(T value) {
        if(value instanceof Map<?,?> map){var copy=new LinkedHashMap<String,Object>();map.forEach((k,v)->copy.put(k.toString(),freeze(v)));return (T)Collections.unmodifiableMap(copy);}
        if(value instanceof List<?> list)return (T)Collections.unmodifiableList(list.stream().map(CustomerContextPacket::freeze).toList());
        if(value instanceof Set<?> set)return (T)Collections.unmodifiableSet(new LinkedHashSet<>(set.stream().map(CustomerContextPacket::freeze).toList()));
        return value;
    }
    static Map<String,Object> freezeMap(Map<String,Object> value){return freeze(value);}
}
