package com.orangejd.server.customer;

import java.util.*;

/** Persistent business state. Model output is a proposal, never an authoritative task snapshot. */
public final class CustomerWorkflow {
    private CustomerWorkflow() {}
    public static final String KEY="workflowV2";
    @SuppressWarnings("unchecked") public static Map<String,Object> map(Object value) { return value instanceof Map<?,?>?(Map<String,Object>)value:new LinkedHashMap<>(); }
    @SuppressWarnings("unchecked") public static List<Map<String,Object>> rows(Object value) { return value instanceof List<?> l?l.stream().filter(Map.class::isInstance).map(v->(Map<String,Object>)v).toList():List.of(); }
    public static Map<String,Object> workflow(Map<String,Object> state){return map(state.get(KEY));}
    public static Map<String,Object> task(Map<String,Object> state){return map(workflow(state).get("task"));}
    public static long number(Map<String,Object> value,String key){return value.get(key) instanceof Number n?n.longValue():0;}
    public static String id(String prefix){return prefix+UUID.randomUUID();}
    public static boolean enabled(Map<String,Object> state){return number(workflow(state),"schemaVersion")==2;}
    public static List<Map<String,Object>> requirements(Map<String,Object> state){return rows(task(state).get("requirements"));}
    public static List<Map<String,Object>> active(Map<String,Object> state){return requirements(state).stream().filter(r->"ACTIVE".equals(r.get("status"))).toList();}
    public static Map<String,Object> question(Map<String,Object> state){return rows(task(state).get("questions")).stream().filter(q->"OPEN".equals(q.get("status"))).findFirst().orElseGet(LinkedHashMap::new);}
    public static void initialize(Map<String,Object> state,List<Map<String,Object>> history,String run) {
        if(enabled(state))return;
        Map<String,Object> wf=new LinkedHashMap<>();wf.put("schemaVersion",2);wf.put("sessionRevision",0L);wf.put("archivedTasks",new ArrayList<>());
        // Preserve the old snapshot for audit/rollback, but none of its execution state is resumed.
        if(!state.isEmpty())wf.put("legacySnapshot",new LinkedHashMap<>(state));
        state.put(KEY,wf);newTask(state,run);
        Map<String,Object> provenance=map(state.get("provenance"));
        List<Map<String,Object>> migrated=new ArrayList<>();
        for(String field:CustomerState.FIELDS) {
            Map<String,Object> source=map(provenance.get(field));String quote=Objects.toString(source.get("quote"),"");
            if(!state.containsKey(field)||!"MESSAGE".equals(source.get("source"))||quote.isBlank())continue;
            history.stream().filter(m->"user".equals(m.get("role")) && Objects.toString(m.get("content"),"").contains(quote)).findFirst().ifPresent(m->{
                var r=CustomerRequirementService.entry(task(state),field,quote,state.get(field),Set.of("goal","experience","topic").contains(field)?"SOFT":"HARD",m.get("id"),quote,"LEGACY_MESSAGE");
                r.put("status","PENDING");migrated.add(r);
            });
        }
        clearProjection(state);task(state).put("requirements",migrated);
        if(wf.containsKey("legacySnapshot"))wf.put("migrationNotice","旧记录已保留。本轮只提取最近40条消息中能核对原话的需求；旧追问、候选顺序和执行进度不自动沿用，更早或无来源的条件请重新说明。");
        if(!migrated.isEmpty()) {wf.put("migrationPending",true);wf.put("migrationFirstRun",run);}
    }
    public static void newTask(Map<String,Object> state,String run) {
        var wf=workflow(state);var old=task(state);
        if(!old.isEmpty()) {
            if(!Set.of("COMPLETED","CANCELLED").contains(Objects.toString(old.get("status"),"")))old.put("status","PAUSED");
            var archive=new ArrayList<>(rows(wf.get("archivedTasks")));archive.add(old);wf.put("archivedTasks",archive);
        }
        Map<String,Object> task=new LinkedHashMap<>();task.put("id",id("task-"));task.put("createdRunId",run);task.put("status","DISCOVERING");task.put("requirementVersion",0L);
        task.put("requirements",new ArrayList<>());task.put("questions",new ArrayList<>());task.put("lists",new ArrayList<>());task.put("events",new ArrayList<>());
        wf.put("task",task);wf.put("activeTaskId",task.get("id"));
    }
    public static void clearProjection(Map<String,Object> state) {
        state.keySet().removeIf(key->!Set.of(KEY,"_expectedStateVersion").contains(key));
    }
    public static void event(Map<String,Object> state,String type,Object details,String run) {
        var task=task(state);var events=new ArrayList<>(rows(task.get("events")));
        events.add(Map.of("type",type,"runId",run,"requirementVersion",number(task,"requirementVersion"),"details",details));task.put("events",events);
    }
    public static Map<String,Object> visible(Map<String,Object> state) {
        var task=task(state);Map<String,Object> view=new LinkedHashMap<>();
        for(String key:List.of("id","status","requirementVersion","selectedProject","lastOutcome","currentListId"))if(task.containsKey(key))view.put(key,task.get(key));
        view.put("requirements",requirements(state).stream().filter(r->!"REMOVED".equals(r.get("status"))).map(r->{var v=new LinkedHashMap<>(r);v.remove("history");return v;}).toList());
        view.put("question",question(state));view.put("migrationPending",Boolean.TRUE.equals(workflow(state).get("migrationPending")));
        view.put("pausedTasks",rows(workflow(state).get("archivedTasks")).stream().filter(t->"PAUSED".equals(t.get("status"))).map(t->Map.of("id",t.get("id"),"requirements",rows(t.get("requirements")).stream().filter(r->"ACTIVE".equals(r.get("status"))).map(r->r.get("text")).toList())).toList());return view;
    }
}
