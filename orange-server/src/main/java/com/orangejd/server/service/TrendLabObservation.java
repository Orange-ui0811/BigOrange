package com.orangejd.server.service;

import java.util.*;
import java.util.function.Supplier;

/** Opt-in, thread-confined diagnostics for the authenticated dev lab only. */
public final class TrendLabObservation implements AutoCloseable {
    private static final ThreadLocal<TrendLabObservation> CURRENT = new ThreadLocal<>();
    final List<Map<String,Object>> stages = new ArrayList<>();
    Map<String,Object> request;
    private java.util.function.Consumer<Map<String,Object>> sink=ignored->{};
    /** The caller explicitly opens this scope inside its worker and carries the request sink across threads. */
    public static TrendLabObservation open(java.util.function.Consumer<Map<String,Object>> sink) {
        var observation=open();observation.sink=sink;return observation;
    }
    private void record(Map<String,Object> stage){stages.add(stage);sink.accept(stage);}
    public static TrendLabObservation open() {
        if (CURRENT.get()!=null) throw new IllegalStateException("Nested lab observation");
        var observation=new TrendLabObservation(); CURRENT.set(observation); return observation;
    }
    public static <T> T measure(String name, Supplier<T> work) {
        var observation=CURRENT.get(); if(observation==null) return work.get();
        long started=System.nanoTime(); String status="SUCCEEDED";
        try { return work.get(); }
        catch(RuntimeException error) { status="FAILED"; throw error; }
        finally { observation.record(Map.of("stage",name,"durationMs",(System.nanoTime()-started)/1_000_000.0,"status",status)); }
    }
    public static void request(Map<String,Object> body) { var current=CURRENT.get(); if(current!=null) current.request=body; }
    public static void elapsed(String name,long started) { var current=CURRENT.get(); if(current!=null) current.record(Map.of("stage",name,"durationMs",(System.nanoTime()-started)/1_000_000.0,"status","SUCCEEDED")); }
    @Override public void close() { CURRENT.remove(); }
}
