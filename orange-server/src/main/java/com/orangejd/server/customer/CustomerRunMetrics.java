package com.orangejd.server.customer;

import java.util.*;

/** Accounts for model usage and orchestration observations without mutating the ledger. */
final class CustomerRunMetrics {
    static Map<String,Object> summarize(List<Map<String,Object>> usage,CustomerProperties config,String promptVersion) {
        Set<String> missing=usage instanceof CustomerUsageLedger ledger?Set.copyOf(ledger.missing):Set.of();
        var orchestration=usage instanceof CustomerUsageLedger ledger?List.copyOf(ledger.orchestration):List.of();
        usage=new ArrayList<>(usage);
        Map<String,Object> metrics=new LinkedHashMap<>(Map.of("modelCalls",new ArrayList<>(usage),"costUsd","UNKNOWN","embeddingUsage","NOT_REPORTED","protocol",config.isNativeActions()?"NATIVE_ACTION_V1":"SCHEMA_ACTION_V1","promptVersion",promptVersion));
        metrics.put("orchestrationTimings",orchestration);metrics.put("observationMissing",missing);metrics.put("tokenUsage",CustomerUsage.summarize(usage)); metrics.put("phaseUsage",CustomerUsage.byPhase(usage)); return metrics;
    }
}
