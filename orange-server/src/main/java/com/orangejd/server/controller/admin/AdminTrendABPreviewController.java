package com.orangejd.server.controller.admin;

import com.orangejd.common.result.Result;
import com.orangejd.pojo.dto.TrendProfileDTO;
import com.orangejd.server.service.TrendRagABPreviewService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("dev")
@RequestMapping("/admin/rag-lab/compare/platform-analysis")
public class AdminTrendABPreviewController {
    private final TrendRagABPreviewService service;
    public AdminTrendABPreviewController(TrendRagABPreviewService service) {this.service=service;}
    public record Request(String variant, TrendProfileDTO profile, boolean generate,boolean observe) {}
    @PostMapping public Result<?> preview(@RequestBody Request request) {
        return Result.success(service.preview(request.variant(),request.profile(),request.generate(),request.observe()));
    }
    public record ReplayRequest(String snapshotId,boolean removeKnowledge) {}
    @PostMapping("/replay") public Result<?> replay(@RequestBody ReplayRequest request) {
        return Result.success(service.replay(request.snapshotId(),request.removeKnowledge()));
    }
}
