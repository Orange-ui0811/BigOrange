package com.orangejd.server.customer;

import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class CustomerRuns {
    private final CustomerStore store;
    private final CustomerMapper db;
    private final CustomerConversation conversation;
    private final CustomerProperties config;
    private final CustomerJson json;
    private final ThreadPoolExecutor workers;
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{ Thread t=new Thread(r,"customer-events"); t.setDaemon(true); return t; });
    private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{ Thread t=new Thread(r,"customer-deadline"); t.setDaemon(true); return t; });
    private final Map<String,Future<?>> futures=new ConcurrentHashMap<>();
    public CustomerRuns(CustomerStore s,CustomerMapper db,CustomerConversation c,CustomerProperties p,CustomerJson json) {
        store=s; this.db=db; conversation=c; config=p; this.json=json;
        BlockingQueue<Runnable> queue=p.getMaxQueued()==0?new SynchronousQueue<>():new ArrayBlockingQueue<>(p.getMaxQueued());
        workers=new ThreadPoolExecutor(p.getMaxConcurrent(),p.getMaxConcurrent(),0,TimeUnit.SECONDS,queue,r->{Thread t=new Thread(r,"customer-run");t.setDaemon(true);return t;});
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recover() { if(config.isEnabled()) for(String id:db.unfinished()) store.terminal(id,"INTERRUPTED","SERVER_RESTARTED",Map.of("costUsd","UNKNOWN")); }
    public Map<String,Object> submit(long session,long owner,String request,String message) {
        Map<String,Object> row=store.submit(session,owner,request,message);
        if(Boolean.TRUE.equals(row.get("replayed"))) return publicRun(row);
        String id=row.get("id").toString(); Instant deadline=acceptedDeadline(row);
        FutureTask<Void> task=new FutureTask<>(()->{
            List<Map<String,Object>> usage=new ArrayList<>();
            try {
                if(!CustomerStore.active(db.run(id)))return null;
                if(!Instant.now().isBefore(deadline)) { store.terminal(id,"TIMED_OUT","DEADLINE_EXCEEDED",Map.of("costUsd","UNKNOWN"));return null; }
                conversation.execute(id,usage,deadline);
            }
            catch(Exception ex) { store.terminal(id,"FAILED",errorCode(ex),conversation.metrics(usage)); }
            finally {
                var finished=db.run(id);
                // Preserve terminal accounting and include late usage reported by the worker.
                if(!usage.isEmpty() || finished==null || finished.get("metrics_json")==null) db.metrics(id,json.write(conversation.metrics(usage)));
                futures.remove(id);
            }
            return null;
        });
        futures.put(id,task);
        try { workers.execute(task); }
        catch(RejectedExecutionException ex) { futures.remove(id); store.terminal(id,"FAILED","CAPACITY_BUSY",Map.of()); return publicRun(db.run(id)); }
        deadlines.schedule(()->{
            if(CustomerStore.active(db.run(id))) { store.terminal(id,"TIMED_OUT","DEADLINE_EXCEEDED",Map.of("costUsd","UNKNOWN")); task.cancel(true);workers.remove(task);futures.remove(id,task); }
        },Math.max(0,java.time.Duration.between(Instant.now(),deadline).toMillis()),TimeUnit.MILLISECONDS);
        return publicRun(row);
    }
    public Map<String,Object> get(String id,long owner) { return publicRun(store.run(id,owner)); }
    public Map<String,Object> cancel(String id,long owner) {
        store.run(id,owner); store.terminal(id,"CANCELLED","USER_CANCELLED",Map.of("costUsd","UNKNOWN"));
        Future<?> future=futures.remove(id); if(future!=null) { future.cancel(true);if(future instanceof Runnable task)workers.remove(task); }
        return get(id,owner);
    }
    public SseEmitter events(String id,long owner) {
        Map<String,Object> row=store.run(id,owner); SseEmitter emitter=new SseEmitter(100_000L);
        try { emitter.send(SseEmitter.event().name("status").data(publicRun(row))); }
        catch(Exception ex) { emitter.completeWithError(ex); return emitter; }
        String[] previous={""}; ScheduledFuture<?>[] future={null};
        Runnable stop=()->{ if(future[0]!=null) future[0].cancel(false); };
        emitter.onCompletion(stop); emitter.onTimeout(stop); emitter.onError(ex->stop.run());
        future[0]=timer.scheduleAtFixedRate(()->{
            try {
                Map<String,Object> current=store.run(id,owner); String marker=current.get("status")+":"+current.get("stage");
                if(!marker.equals(previous[0])) { emitter.send(SseEmitter.event().name("status").data(publicRun(current))); previous[0]=marker; }
                if(!CustomerStore.active(current)) { stop.run(); emitter.complete(); }
            } catch(Exception ex) { stop.run(); emitter.completeWithError(ex); }
        },100,500,TimeUnit.MILLISECONDS);
        return emitter;
    }
    private Map<String,Object> publicRun(Map<String,Object> row) {
        Map<String,Object> safe=new LinkedHashMap<>();
        for(String key:List.of("id","session_id","status","stage","result_json","metrics_json","error_code","created_at","finished_at","generation_key","prompt_version")) if(row.containsKey(key)) safe.put(key,row.get(key));
        return safe;
    }
    private String errorCode(Exception ex) {
        String message=ex.getMessage();
        if(message!=null && message.matches("[A-Z][A-Z0-9_]{2,70}")) return message;
        return ex instanceof IllegalArgumentException?"INVALID_MODEL_OUTPUT":"DEPENDENCY_FAILED";
    }
    private Instant acceptedDeadline(Map<String,Object> row) {
        Instant fallback=Instant.now().plusSeconds(config.getRunSeconds());Object value=row.get("deadline_at");
        Instant stored=null;
        if(value instanceof java.sql.Timestamp time)stored=time.toInstant();
        else if(value instanceof java.time.LocalDateTime time)stored=time.atZone(java.time.ZoneId.systemDefault()).toInstant();
        else if(value instanceof Instant time)stored=time;
        return stored!=null && stored.isBefore(fallback)?stored:fallback;
    }
    @PreDestroy public void close() { workers.shutdownNow(); timer.shutdownNow(); deadlines.shutdownNow(); }
}
