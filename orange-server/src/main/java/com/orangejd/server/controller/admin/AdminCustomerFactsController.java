package com.orangejd.server.controller.admin;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.Result;
import com.orangejd.server.customer.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Protected by the existing ADMIN interceptor. No raw source text is added to consumer responses. */
@RestController
@RequestMapping("/admin/customer/source-facts")
public class AdminCustomerFactsController {
    private final CustomerSourceFactService facts;
    private final CustomerFactRefresh refresh;
    public AdminCustomerFactsController(CustomerSourceFactService facts,CustomerFactRefresh refresh) { this.facts=facts;this.refresh=refresh; }
    @GetMapping public Result<?> list() { return Result.success(facts.list()); }
    @GetMapping("/{id}/events") public Result<?> events(@PathVariable String id) { return Result.success(facts.events(id)); }
    @PostMapping public Result<?> propose(@RequestBody Map<String,Object> fact) { return Result.success(facts.propose(fact)); }
    @PostMapping("/refresh") public Result<?> refresh() { return Result.success(refresh.enqueue()); }
    @PostMapping("/{id}/review") public Result<?> review(@PathVariable String id,@RequestBody Review input) {
        facts.review(id,input.revision(),input.status(),BaseContext.getCurrentId(),input.reason());return Result.success();
    }
    public record Review(long revision,String status,String reason) {}
}
