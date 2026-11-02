package com.orangejd.server.controller.admin;

import com.orangejd.common.result.Result;
import com.orangejd.server.customer.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Administrative diagnostics without account profile, conversation content, prompts or raw evidence. */
@RestController
@RequestMapping("/admin/customer/runs")
public class AdminCustomerRunsController {
    private final CustomerMapper db;
    private final CustomerJson json;
    private final CustomerGraphRepository graph;
    private final CustomerProperties config;
    public AdminCustomerRunsController(CustomerMapper db,CustomerJson json,CustomerGraphRepository graph,CustomerProperties config) {this.db=db;this.json=json;this.graph=graph;this.config=config;}
    @GetMapping public Result<?> list(@RequestParam(required=false) String runId,@RequestParam(required=false) String engine,@RequestParam(required=false) String status,@RequestParam(required=false) String outcome,@RequestParam(required=false) String from,@RequestParam(required=false) String until,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        int limit=Math.max(1,Math.min(100,size));int offset=(Math.max(1,Math.min(10000,page))-1)*limit;
        return Result.success(graph.runs(runId,engine,status,outcome,from,until,offset,limit));
    }
    @GetMapping("/{id}/diagnostics") public Result<?> diagnostics(@PathVariable String id) {
        var row=db.run(id);if(row==null)throw new IllegalArgumentException("RUN_NOT_FOUND");
        var result=json.read(row.get("result_json"));Map<String,Object> out=new LinkedHashMap<>();
        for(String key:List.of("id","status","stage","error_code","created_at","finished_at","generation_key","prompt_version","execution_engine","graph_version","trace_id","state_schema_version"))if(row.containsKey(key))out.put(key,row.get(key));
        out.put("businessOutcome",result.getOrDefault("businessOutcome",CustomerOutcome.classify(result)));
        out.put("metrics",json.read(row.get("metrics_json")));
        out.put("steps",db.steps(id).stream().map(step->{Map<String,Object> s=new LinkedHashMap<>();for(String k:List.of("id","tool_name","status","created_at"))if(step.containsKey(k))s.put(k,step.get(k));return s;}).toList());
        out.put("nodes",graph.nodes(id));out.put("payloads",graph.payloads(id));out.put("rawAccessEnabled",config.isRawAccessEnabled());
        out.put("retrievalTimings",db.steps(id).stream().filter(s->"retrievalTiming".equals(s.get("tool_name"))).map(s->json.read(s.get("detail_json"))).toList());
        out.put("orchestrationTimings",json.read(row.get("metrics_json")).getOrDefault("orchestrationTimings",List.of()));
        return Result.success(out);
    }
    @GetMapping("/{id}/checkpoints") public Result<?> checkpoints(@PathVariable String id) {
        if(db.run(id)==null)throw new IllegalArgumentException("RUN_NOT_FOUND");
        List<Map<String,Object>> checkpoints=new ArrayList<>();Map<String,Object> previous=Map.of();
        for(var row:graph.checkpoints(id).reversed()){
            Map<String,Object> safe=new LinkedHashMap<>();for(String key:List.of("sequence_id","checkpoint_id","node_id","next_node_id","created_at"))if(row.containsKey(key))safe.put(key,row.get(key));
            var state=json.read(row.get("state_json"));Map<String,Object> summary=new LinkedHashMap<>();
            for(String key:List.of("next","intent","businessStatus","candidateIdentities","evidenceIds","generation"))if(state.containsKey(key))summary.put(key,state.get(key));
            Map<String,Object> conditions=new LinkedHashMap<>();
            if(state.get("conditions") instanceof Map<?,?> fields)for(String field:CustomerState.FIELDS)if(fields.containsKey(field))conditions.put(field,fields.get(field));
            List<Map<String,Object>> changes=new ArrayList<>();
            for(String field:CustomerState.FIELDS)if(!Objects.equals(previous.get(field),conditions.get(field))){var change=new LinkedHashMap<String,Object>();change.put("field",field);change.put("before",previous.get(field));change.put("after",conditions.get(field));changes.add(change);}
            summary.put("conditionChanges",changes);previous=conditions;
            safe.put("summary",summary);checkpoints.add(safe);
        }
        return Result.success(checkpoints.reversed());
    }
    public record RawAccess(String payloadId,String reason) {}
    @PostMapping("/{id}/raw-access") public Result<?> raw(@PathVariable String id,@RequestBody RawAccess request) {
        Long actor=com.orangejd.common.context.BaseContext.getCurrentId();
        if(actor==null || !config.isRawAccessEnabled())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"原文查看未启用");
        if(request.payloadId()==null || request.reason()==null || request.reason().isBlank() || request.reason().length()>500)throw new IllegalArgumentException("INVALID_RAW_ACCESS_REASON");
        if(db.run(id)==null)throw new IllegalArgumentException("RUN_NOT_FOUND");
        return Result.success(graph.raw(actor,id,request.payloadId(),request.reason().strip()));
    }
}
