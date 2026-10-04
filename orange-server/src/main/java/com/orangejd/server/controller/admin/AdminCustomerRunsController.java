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
    public AdminCustomerRunsController(CustomerMapper db,CustomerJson json) {this.db=db;this.json=json;}
    @GetMapping("/{id}/diagnostics") public Result<?> diagnostics(@PathVariable String id) {
        var row=db.run(id);if(row==null)throw new IllegalArgumentException("RUN_NOT_FOUND");
        var result=json.read(row.get("result_json"));Map<String,Object> out=new LinkedHashMap<>();
        for(String key:List.of("id","status","stage","error_code","created_at","finished_at","generation_key","prompt_version"))if(row.containsKey(key))out.put(key,row.get(key));
        out.put("businessOutcome",result.getOrDefault("businessOutcome",CustomerOutcome.classify(result)));
        out.put("metrics",json.read(row.get("metrics_json")));
        out.put("steps",db.steps(id).stream().map(step->{Map<String,Object> s=new LinkedHashMap<>();for(String k:List.of("id","tool_name","status","created_at"))if(step.containsKey(k))s.put(k,step.get(k));return s;}).toList());
        return Result.success(out);
    }
}
