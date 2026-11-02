package com.orangejd.server.customer;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Task-attributed metadata only. No copied archive and no update of business state. */
@Repository
public class CustomerContextRepository {
    private final CustomerContextMapper mapper;
    private final CustomerJson json;
    public CustomerContextRepository(CustomerContextMapper mapper,CustomerJson json){this.mapper=mapper;this.json=json;}
    public void requireOwned(CustomerHistoryRecovery.Scope scope) {
        Long owned=mapper.owned(scope);
        if(owned==null||owned.longValue()!=scope.sessionId())throw new SecurityException("CONTEXT_SOURCE_NOT_ACCESSIBLE");
    }
    public void checkScope(CustomerHistoryRecovery.Scope scope){requireOwned(scope);requireActive(scope,false);}
    private void requireActive(CustomerHistoryRecovery.Scope scope,boolean requireBinding) {
        var run=mapper.run(scope);
        if(run==null||!Set.of("RUNNING","QUEUED").contains(run.get("status")))throw new IllegalStateException("RUN_STOPPED");
        if(requireBinding&&(!scope.taskId().equals(run.get("task_id"))||!(run.get("requirement_version") instanceof Number n)||n.longValue()!=scope.requirementVersion()))throw new IllegalStateException("STALE_CONTEXT_TASK");
    }
    @Transactional
    public void bind(CustomerHistoryRecovery.Scope scope) {
        requireOwned(scope);requireActive(scope,false);mapper.bind(scope);requireActive(scope,true);
    }
    public Map<String,Object> source(CustomerHistoryRecovery.Scope scope,long message) {
        requireOwned(scope);
        if(message<=0||message>scope.watermark())throw new IllegalArgumentException("CONTEXT_SOURCE_OUTSIDE_WATERMARK");
        var row=mapper.source(scope,message);
        if(row==null||!(row.get("id") instanceof Number id)||id.longValue()!=message)throw new IllegalArgumentException("CONTEXT_SOURCE_NOT_FOUND");
        return row;
    }
    public List<Map<String,Object>> search(CustomerHistoryRecovery.Scope scope,List<String> terms,boolean otherTasks) {
        requireOwned(scope);if(terms.isEmpty())return List.of();
        return mapper.search(scope,terms,otherTasks);
    }
    public List<Map<String,Object>> corrections(CustomerHistoryRecovery.Scope scope,long after,boolean otherTasks) {
        requireOwned(scope);return mapper.subsequentUsers(scope,after,otherTasks);
    }
    public List<Map<String,Object>> completed(CustomerHistoryRecovery.Scope scope,long after) {
        requireOwned(scope);return mapper.completed(scope,after);
    }
    public List<Map<String,Object>> recent(CustomerHistoryRecovery.Scope scope,int limit) {
        requireOwned(scope);if(limit<1||limit>80)throw new IllegalArgumentException("INVALID_CONTEXT_WINDOW");
        var rows=new ArrayList<>(mapper.recent(scope,limit));Collections.reverse(rows);return List.copyOf(rows);
    }
    public List<Map<String,Object>> sourceRows(CustomerHistoryRecovery.Scope scope,List<Long> ids) {
        requireOwned(scope);
        if(ids.isEmpty()||ids.size()>160||ids.stream().anyMatch(id->id<=0||id>scope.watermark()))throw new IllegalArgumentException("INVALID_SUMMARY_SOURCES");
        var rows=mapper.sources(scope,ids);
        if(rows.size()!=ids.size()||!rows.stream().map(r->number(r,"id")).toList().equals(ids.stream().sorted().toList()))throw new IllegalArgumentException("INVALID_SUMMARY_SOURCES");
        return rows;
    }
    public List<Map<String,Object>> summaries(CustomerHistoryRecovery.Scope scope) {
        requireOwned(scope);return mapper.summaries(scope);
    }
    @Transactional
    public String claimSummary(CustomerHistoryRecovery.Scope scope) {
        requireOwned(scope);requireActive(scope,true);String token=UUID.randomUUID().toString();
        mapper.ensureLease(scope);return mapper.claim(scope,token)==1?token:null;
    }
    public void releaseSummary(CustomerHistoryRecovery.Scope scope,String token){mapper.release(scope,token);}
    @Transactional
    public boolean saveSummary(CustomerHistoryRecovery.Scope scope,String lease,List<Map<String,Object>> originals,String expectedHash,String summary,String model) {
        Long owned=mapper.lockOwned(scope);
        if(owned==null||owned.longValue()!=scope.sessionId())throw new SecurityException("CONTEXT_SOURCE_NOT_ACCESSIBLE");
        requireActive(scope,true);
        if(mapper.leased(scope,lease)!=1)return false;
        var ids=originals.stream().map(r->number(r,"id")).toList();
        var current=sourceRows(scope,ids);
        if(!expectedHash.equals(sourceHash(current))||current.stream().anyMatch(r->!scope.taskId().equals(r.get("task_id"))))throw new IllegalArgumentException("SUMMARY_SOURCE_CHANGED");
        // Check all persisted segments, not only the bounded model-facing summary window.
        if(mapper.overlapping(scope,Collections.min(ids),Collections.max(ids))>0)return false;
        Map<String,Object> row=new LinkedHashMap<>();row.put("id",UUID.randomUUID().toString());row.put("sessionId",scope.sessionId());row.put("taskId",scope.taskId());row.put("requirementVersion",scope.requirementVersion());
        row.put("fromMessageId",Collections.min(ids));row.put("throughMessageId",Collections.max(ids));row.put("sourceIdsJson",json.write(Map.of("ids",ids)));row.put("sourceHash",expectedHash);row.put("summary",summary);row.put("model",model);
        return mapper.insertSummary(row)==1;
    }
    public List<Long> ids(Map<String,Object> row) {
        Object raw=json.read(row.get("source_ids_json")).get("ids");
        if(!(raw instanceof List<?> list))throw new IllegalArgumentException("INVALID_SUMMARY_SOURCES");
        List<Long> ids=new ArrayList<>();for(Object item:list)if(item instanceof Number n&&n.longValue()>0&&n.doubleValue()==n.longValue())ids.add(n.longValue());else throw new IllegalArgumentException("INVALID_SUMMARY_SOURCES");
        if(ids.size()!=new HashSet<>(ids).size()||ids.isEmpty())throw new IllegalArgumentException("INVALID_SUMMARY_SOURCES");return ids;
    }
    public static String sourceHash(List<Map<String,Object>> rows) {
        StringBuilder value=new StringBuilder("context-source-v1");
        for(var row:rows.stream().sorted(Comparator.comparingLong(r->number(r,"id"))).toList()) {
            value.append('|').append(number(row,"id")).append('|').append(row.get("run_id")).append('|').append(row.get("role")).append('|').append(row.get("task_id"));
            value.append('|').append(CustomerStore.hash(Objects.toString(row.get("content"),"")));
        }
        return CustomerStore.hash(value.toString());
    }
    static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
}
