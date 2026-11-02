package com.orangejd.server.customer;

import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;
import java.util.*;

/** Explicit request reference travels with the usage list and tool context, not a ThreadLocal. */
@Service
public class CustomerExecutionTrace {
    private final CustomerGraphRepository repository;private final CustomerJson json;private final MeterRegistry metrics;
    public CustomerExecutionTrace(CustomerGraphRepository repository,CustomerJson json,MeterRegistry metrics){this.repository=repository;this.json=json;this.metrics=metrics;}
    void node(CustomerConversation.Turn turn,String engine,String node) {
        String id=UUID.randomUUID().toString();long start=System.nanoTime();
        if(turn.usage instanceof CustomerUsageLedger ledger)ledger.nodeId=id;
        if(!safe(()->repository.nodeStart(id,turn.id,engine,node)) && turn.usage instanceof CustomerUsageLedger ledger)ledger.missing.add("NODE_START");
        String status="SUCCEEDED",error=null;
        try {turn.step(node);}
        catch(RuntimeException ex){status="FAILED";error=CustomerRecovery.code(ex);throw ex;}
        finally {
            long ms=(System.nanoTime()-start)/1_000_000;String outcome=status,code=error;
            if(!safe(()->repository.nodeEnd(id,outcome,turn.next(),ms,code,json.write(turn.snapshot()))) && turn.usage instanceof CustomerUsageLedger ledger)ledger.missing.add("NODE_END");
            safe(()->metrics.timer("customer.node.duration","engine",engine,"node",node,"status",outcome).record(ms,java.util.concurrent.TimeUnit.MILLISECONDS));
        }
    }
    public boolean payload(String run,String node,String kind,Object value) {
        return safe(()->repository.payload(UUID.randomUUID().toString(),run,node,kind,json.write(value)));
    }
    public void timing(String run,String node,Map<String,Object> value) {
        Map<String,Object> detail=new LinkedHashMap<>(value);detail.put("nodeExecutionId",node);
        safe(()->repository.technicalStep(run,"retrievalTiming",json.write(detail)));
    }
    public void archive(String run,String status,String error) {safe(()->repository.archive(run,status,error));}
    private boolean safe(Runnable work){try{work.run();return true;}catch(RuntimeException ex){LoggerFactory.getLogger(getClass()).warn("Customer observation unavailable: {}",ex.getClass().getSimpleName());return false;}}
    @org.springframework.scheduling.annotation.Scheduled(cron="0 20 3 * * *")
    public void cleanup(){safe(repository::cleanup);}
}
