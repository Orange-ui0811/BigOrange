package com.orangejd.server.controller.app;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.Result;
import com.orangejd.server.customer.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;

@RestController
@RequestMapping("/app/customer")
public class AppCustomerController {
    private final CustomerStore store;
    private final CustomerRuns runs;
    private final CustomerProperties config;
    private final CustomerImportService imports;
    public AppCustomerController(CustomerStore s,CustomerRuns r,CustomerProperties p,CustomerImportService imports) { store=s; runs=r; config=p; this.imports=imports; }
    @GetMapping("/capabilities") public Result<?> capabilities() {
        long owner=owner(); return Result.success(Map.of("enabled",config.canAccess(owner),"accountId",owner,"model",config.getModel(),"thinking",config.isThinking(),"protocol",config.isNativeActions()?"NATIVE_ACTION_V1":"SCHEMA_ACTION_V1","skills",CustomerConversation.SKILLS));
    }
    @PostMapping("/sessions") public Result<?> create(@RequestBody(required=false) Map<String,String> body) { return Result.success(store.create(owner(),body==null?null:body.get("title"))); }
    @GetMapping("/sessions") public Result<?> sessions() { return Result.success(store.sessions(owner())); }
    @DeleteMapping("/sessions/{id}") public Result<?> deleteSession(@PathVariable long id) { store.deleteSession(id,owner()); return Result.success(); }
    @GetMapping("/sessions/{id}/messages") public Result<?> history(@PathVariable long id,@RequestParam(defaultValue="9223372036854775807") long before,@RequestParam(defaultValue="50") int limit) { return Result.success(store.history(id,owner(),before,limit)); }
    @PostMapping("/sessions/{id}/runs") public Result<?> submit(@PathVariable long id,@RequestBody RunInput input) { return Result.success(runs.submit(id,owner(),input.requestId(),input.message())); }
    @GetMapping("/runs/{id}") public Result<?> run(@PathVariable String id) { return Result.success(runs.get(id,owner())); }
    @GetMapping(value="/runs/{id}/events",produces="text/event-stream") public SseEmitter events(@PathVariable String id) { return runs.events(id,owner()); }
    @PostMapping("/runs/{id}/cancel") public Result<?> cancel(@PathVariable String id) { return Result.success(runs.cancel(id,owner())); }
    @PostMapping("/runs/{id}/import-draft") public Result<?> importDraft(@PathVariable String id,@RequestBody ImportInput input) { return Result.success(imports.draft(id,owner(),input.type(),input.projectId())); }
    private long owner() { Long id=BaseContext.getCurrentId(); if(id==null || id<=0) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"请先登录"); return id; }
    public record RunInput(String requestId,String message) {}
    public record ImportInput(String type,long projectId) {}
    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Result<String>> status(ResponseStatusException ex) {
        return org.springframework.http.ResponseEntity.status(ex.getStatusCode()).body(Result.error(ex.getReason()));
    }
}
