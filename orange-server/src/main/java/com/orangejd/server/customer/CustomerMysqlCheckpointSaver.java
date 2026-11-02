package com.orangejd.server.customer;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One saver per run; diagnostic history never resumes another request. */
public final class CustomerMysqlCheckpointSaver implements BaseCheckpointSaver {
    private final String run;
    private final CustomerGraphRepository repository;
    private final CustomerJson json;
    private final CustomerUsageLedger ledger;
    private List<Checkpoint> cached;
    public CustomerMysqlCheckpointSaver(String run,CustomerGraphRepository repository,CustomerJson json) {this(run,repository,json,null);}
    public CustomerMysqlCheckpointSaver(String run,CustomerGraphRepository repository,CustomerJson json,CustomerUsageLedger ledger) {this.run=run;this.repository=repository;this.json=json;this.ledger=ledger;}
    private void scope(RunnableConfig config) {
        if(!("customer:"+run).equals(config.threadId().orElse("")))throw new IllegalArgumentException("CHECKPOINT_RUN_MISMATCH");
    }
    private List<Checkpoint> history() {
        if(cached==null) {
            long started=System.nanoTime();
            try {cached=new ArrayList<>(repository.checkpoints(run).stream().map(row->Checkpoint.builder().id(row.get("checkpoint_id").toString()).nodeId(row.get("node_id").toString()).nextNodeId((String)row.get("next_node_id")).state(json.read(row.get("state_json"))).build()).toList());}
            finally {if(ledger!=null)ledger.timing("checkpoint_read",System.nanoTime()-started);}
        }
        return cached;
    }
    private Checkpoint copy(Checkpoint value) {
        return Checkpoint.builder().id(value.getId()).nodeId(value.getNodeId()).nextNodeId(value.getNextNodeId()).state(json.read(json.write(value.getState()))).build();
    }
    @Override public synchronized Collection<Checkpoint> list(RunnableConfig config) {
        scope(config);
        return history().stream().map(this::copy).toList();
    }
    @Override public synchronized Optional<Checkpoint> get(RunnableConfig config) {
        scope(config);
        return history().stream().filter(c->config.checkPointId().isEmpty() || config.checkPointId().get().equals(c.getId())).findFirst().map(this::copy);
    }
    @Override public synchronized RunnableConfig put(RunnableConfig config,Checkpoint checkpoint) {
        scope(config);String state=json.write(checkpoint.getState());
        if(state.getBytes(StandardCharsets.UTF_8).length>CustomerGraphSerializer.MAX_BYTES)throw new IllegalStateException("CHECKPOINT_TOO_LARGE");
        long started=System.nanoTime();
        try {
            repository.checkpoint(run,checkpoint.getId(),checkpoint.getNodeId(),checkpoint.getNextNodeId(),state);
            // Cache only AFTER the durable write succeeds. Every run owns its saver.
            if(cached!=null) {
                int previous=-1;for(int i=0;i<cached.size();i++)if(checkpoint.getId().equals(cached.get(i).getId())){previous=i;break;}
                if(previous>=0)cached.set(previous,copy(checkpoint));else cached.addFirst(copy(checkpoint));
            }
        }
        catch(RuntimeException ex){throw new IllegalStateException("CHECKPOINT_WRITE_FAILED",ex);}
        finally {if(ledger!=null)ledger.timing("checkpoint_write",System.nanoTime()-started);}
        return RunnableConfig.builder(config).checkPointId(checkpoint.getId()).build();
    }
    @Override public Tag release(RunnableConfig config) {
        scope(config);repository.archive(run,"GRAPH_COMPLETE",null);
        return new Tag("customer:"+run,List.of());
    }
}
