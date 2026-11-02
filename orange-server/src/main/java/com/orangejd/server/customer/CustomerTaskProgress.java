package com.orangejd.server.customer;

import java.util.*;

/** A compact execution checkpoint, distinct from user conditions and conversational summaries. */
final class CustomerTaskProgress {
    private CustomerTaskProgress() {}
    static final String KEY="recommendationTask";
    static void advance(Map<String,Object> state,String run,String phase) {
        state.put(KEY,Map.of("runId",run,"phase",phase,"status","RUNNING","topic",CustomerPurpose.topic(state)));
    }
    static void finish(Map<String,Object> state,String run,Object outcome) {
        if(state.get(KEY) instanceof Map<?,?> task && run.equals(task.get("runId"))) {
            Map<String,Object> value=new LinkedHashMap<>();task.forEach((k,v)->value.put(k.toString(),v));
            value.put("status",Objects.toString(outcome,"UNKNOWN"));state.put(KEY,value);
        }
    }
    static void recover(Map<String,Object> state,CustomerMapper db,long session,String currentRun) {
        if(!(state.get(KEY) instanceof Map<?,?> task) || !"RUNNING".equals(task.get("status")) || !(task.get("runId") instanceof String previous) || previous.equals(currentRun))return;
        var run=db.run(previous);
        if(run==null || !(run.get("session_id") instanceof Number id) || id.longValue()!=session)return;
        if(!CustomerStore.active(run))finish(state,previous,run.get("status"));
    }
    static Map<String,Object> visible(Map<String,Object> state) {
        Map<String,Object> view=new LinkedHashMap<>();
        if(state.get(KEY) instanceof Map<?,?> task)for(String k:List.of("phase","status","topic"))if(task.containsKey(k))view.put(k,task.get(k));
        return view;
    }
    static String describe(Map<String,Object> state) {
        var task=visible(state);if(task.isEmpty())return "";
        String phase=switch(Objects.toString(task.get("phase"),"")) {
            case "PLANNING"->"整理检索表达";case "RETRIEVING"->"查找项目";case "REVIEWING"->"核对项目用途";case "ENRICHING"->"补充项目证据和说明";default->"处理推荐";
        };
        String status=Objects.toString(task.get("status"),"");
        if(Set.of("CANCELLED","TIMED_OUT","INTERRUPTED","FAILED").contains(status))return "上次推荐在“"+phase+"”阶段停止了，已确认条件仍保留。继续推荐时会重新检查当前项目资料，只复用仍有效的核对结果。\n\n";
        return "";
    }
}
