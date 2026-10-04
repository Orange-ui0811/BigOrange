package com.orangejd.server.service;

import java.util.*;
import java.util.function.Supplier;

/** Opt-in, thread-confined diagnostics for the authenticated dev lab only. */
public final class TrendLabObservation implements AutoCloseable {
    private static final ThreadLocal<TrendLabObservation> CURRENT = new ThreadLocal<>();
    final List<Map<String,Object>> stages = new ArrayList<>();
    Map<String,Object> request;
    public static TrendLabObservation open() {
        if (CURRENT.get()!=null) throw new IllegalStateException("Nested lab observation");
        var observation=new TrendLabObservation(); CURRENT.set(observation); return observation;
    }
    public static <T> T measure(String name, Supplier<T> work) {
        var observation=CURRENT.get(); if(observation==null) return work.get();
        long started=System.nanoTime(); String status="SUCCEEDED";
        try { return work.get(); }
        catch(RuntimeException error) { status="FAILED"; throw error; }
        finally { observation.stages.add(Map.of("stage",name,"durationMs",(System.nanoTime()-started)/1_000_000.0,"status",status)); }
    }
    public static void request(Map<String,Object> body) { var current=CURRENT.get(); if(current!=null) current.request=body; }
    public static void elapsed(String name,long started) { var current=CURRENT.get(); if(current!=null) current.stages.add(Map.of("stage",name,"durationMs",(System.nanoTime()-started)/1_000_000.0,"status","SUCCEEDED")); }
    @Override public void close() { CURRENT.remove(); }
}
