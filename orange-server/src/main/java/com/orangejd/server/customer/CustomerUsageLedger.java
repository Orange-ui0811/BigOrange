package com.orangejd.server.customer;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Attempt values are published only after finalizing them; readers see a stable snapshot. */
public final class CustomerUsageLedger extends CopyOnWriteArrayList<Map<String,Object>> {
    final String runId;final CustomerExecutionTrace trace;volatile String nodeId;
    volatile Runnable guard=()->{};
    final Set<String> missing=java.util.concurrent.ConcurrentHashMap.newKeySet();
    final List<Map<String,Object>> orchestration=new CopyOnWriteArrayList<>();
    CustomerUsageLedger(String runId,CustomerExecutionTrace trace){this.runId=runId;this.trace=trace;}
    public void payload(String kind,Object value){if(!trace.payload(runId,nodeId,kind,value))missing.add(kind);}
    public void check(){guard.run();}
    void timing(String component,long nanos){orchestration.add(Map.of("component",component,"durationMs",nanos/1_000_000.0));}
}
