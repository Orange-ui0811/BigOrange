package com.orangejd.server.customer;

import java.time.Instant;
import java.util.*;

/** Session-scoped, bounded semantic decisions. Reuse never substitutes for current business checks. */
final class CustomerPurposeMemory {
    private CustomerPurposeMemory() {}
    static final String KEY="purposeMemory";
    static final int LIMIT=48;
    private static final long TTL_SECONDS=1800;
    private static String signature(Map<String,Object> state,String model) {
        return CustomerStore.hash("purpose-memory-v1|"+CustomerPurpose.REVIEW_PROMPT+"|"+model+"|"+CustomerPurpose.scope(state));
    }
    private static Map<String,Object> entries(Map<String,Object> state,String model) {
        Map<String,Object> out=new LinkedHashMap<>();
        if(state.get(KEY) instanceof Map<?,?> memory && signature(state,model).equals(memory.get("signature")) && memory.get("entries") instanceof Map<?,?> values)
            values.forEach((key,value)->{if(value instanceof Map<?,?> entry && fresh(entry.get("at")))out.put(key.toString(),value);});
        return out;
    }
    private static boolean fresh(Object at) {
        try { Instant time=Instant.parse(Objects.toString(at,""));return !time.isAfter(Instant.now().plusSeconds(5)) && time.plusSeconds(TTL_SECONDS).isAfter(Instant.now()); }
        catch(RuntimeException ex) { return false; }
    }
    private static Map<?,?> decision(Map<String,Object> p,Map<String,Object> entries) {
        Object value=entries.get(CustomerPurpose.key(p));
        return value instanceof Map<?,?> entry && CustomerPurpose.fingerprint(p).equals(entry.get("fingerprint"))?entry:null;
    }
    static List<Map<String,Object>> pending(List<Map<String,Object>> candidates,Map<String,Object> state,String model) {
        var entries=entries(state,model);return candidates.stream().filter(p->decision(p,entries)==null).toList();
    }
    static void remember(Map<String,Object> output,Map<String,Object> input,List<Map<String,Object>> candidates,Map<String,Object> state,String model) {
        Map<String,Object> checked=new LinkedHashMap<>(state);
        CustomerPurpose.apply(output,input,candidates,checked); // Validate every identity and quote before caching anything.
        var proofs=(Map<?,?>)((Map<?,?>)checked.get(CustomerPurpose.STATE_KEY)).get("proofs");
        var entries=entries(state,model);
        Set<String> supplied=new HashSet<>();for(Object raw:(List<?>)input.get("candidates"))supplied.add(((Map<?,?>)raw).get("key").toString());
        String batch=UUID.randomUUID().toString();
        Set<?> unreviewed=output.get("unreviewedKeys") instanceof List<?> keys?new HashSet<>(keys):Set.of();
        for(var p:candidates)if(supplied.contains(CustomerPurpose.key(p)) && !unreviewed.contains(CustomerPurpose.key(p))) {
            String key=CustomerPurpose.key(p);Map<String,Object> entry=new LinkedHashMap<>();
            entry.put("fingerprint",CustomerPurpose.fingerprint(p));entry.put("at",Instant.now().toString());
            if(proofs.get(key) instanceof Map<?,?> proof) {entry.put("quote",proof.get("quote"));entry.put("relation",proof.get("relation"));entry.put("rank",proof.get("rank"));entry.put("batch",batch);}
            entries.remove(key);entries.put(key,entry);
        }
        while(entries.size()>LIMIT)entries.remove(entries.keySet().iterator().next());
        state.put(KEY,Map.of("signature",signature(state,model),"entries",entries));
    }
    /** Returns false when no positive result is usable and some candidates remain unreviewed. */
    static boolean restore(List<Map<String,Object>> candidates,Map<String,Object> state,String model) {
        var entries=entries(state,model);Map<String,Object> proofs=new LinkedHashMap<>();Set<String> batches=new HashSet<>();int reviewed=0;
        for(var p:candidates) {
            Map<?,?> entry=decision(p,entries);if(entry==null)continue;reviewed++;
            if(entry.get("quote") instanceof String quote) {
                int rank=entry.get("rank") instanceof Number n?n.intValue():proofs.size()+1;
                proofs.put(CustomerPurpose.key(p),new LinkedHashMap<>(Map.of("fingerprint",CustomerPurpose.fingerprint(p),"quote",quote,"relation",entry.get("relation"),"rank",rank)));
                batches.add(Objects.toString(entry.get("batch"),"legacy"));
            }
        }
        // Relative ranks from different review batches cannot be compared. Let existing retrieval/
        // evidence/business scores break those ties; preserve the model order within a single batch.
        if(batches.size()>1)for(Object raw:proofs.values())((Map<String,Object>)raw).put("rank",1);
        boolean complete=reviewed==candidates.size();
        if(!complete && proofs.isEmpty())return false;
        String status=complete?(proofs.isEmpty()?"SUCCEEDED_EMPTY":"SUCCEEDED_MATCHES"):"PARTIAL_MATCHES";
        state.put(CustomerPurpose.STATE_KEY,Map.of("status",status,"scope",CustomerPurpose.scope(state),"topic",CustomerPurpose.topic(state),"proofs",proofs,"reviewedCount",reviewed,"candidateCount",candidates.size()));
        return true;
    }
    static String plannedQuery(Map<String,Object> state,String model) {
        if(state.get("queryPlan") instanceof Map<?,?> plan && planSignature(state,model).equals(plan.get("signature")) && fresh(plan.get("at")) && plan.get("query") instanceof String query && !query.isBlank() && query.length()<=2000)return query;
        return null;
    }
    static void rememberPlan(Map<String,Object> state,String model,String query) {
        state.put("queryPlan",Map.of("signature",planSignature(state,model),"query",query,"at",Instant.now().toString()));
    }
    private static String planSignature(Map<String,Object> state,String model) {
        return CustomerStore.hash(CustomerPurpose.PLAN_PROMPT+"|"+model+"|"+CustomerPurpose.scope(state));
    }
}
