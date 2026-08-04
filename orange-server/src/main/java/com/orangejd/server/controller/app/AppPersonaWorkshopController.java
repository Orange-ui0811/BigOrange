package com.orangejd.server.controller.app;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.result.Result;
import com.orangejd.server.dto.PersonaEntryRequest;
import com.orangejd.server.dto.PersonaAccessoryBlueprintRequest;
import com.orangejd.server.dto.PersonaEquipmentRequest;
import com.orangejd.server.dto.PersonaMaterialDecisionRequest;
import com.orangejd.server.dto.PersonaSettingsRequest;
import com.orangejd.server.service.PersonaWorkshopService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/app/persona-workshop")
public class AppPersonaWorkshopController {
    private final PersonaWorkshopService service;

    public AppPersonaWorkshopController(PersonaWorkshopService service) {
        this.service = service;
    }

    @GetMapping
    public Result<?> snapshot() {
        return Result.success(service.snapshot(accountId()));
    }

    @GetMapping("/public/{accountId}")
    public Result<?> publicSnapshot(@PathVariable Long accountId) {
        return Result.success(service.publicSnapshot(accountId));
    }

    @GetMapping("/avatar")
    public Result<?> avatar() {
        return Result.success(service.avatarSnapshot(accountId()));
    }

    @PostMapping("/entries")
    public Result<?> createEntry(@Valid @RequestBody PersonaEntryRequest request) {
        return Result.success(service.createEntry(accountId(), request));
    }

    @PutMapping("/entries/{id}")
    public Result<?> updateEntry(@PathVariable Long id,
                                 @Valid @RequestBody PersonaEntryRequest request) {
        return Result.success(service.updateEntry(accountId(), id, request));
    }

    @DeleteMapping("/entries/{id}")
    public Result<?> deleteEntry(@PathVariable Long id) {
        service.archiveEntry(accountId(), id);
        return Result.success();
    }

    @GetMapping("/materials")
    public Result<?> materials(@RequestParam(required = false) String status) {
        return Result.success(service.materials(accountId(), status));
    }

    @PostMapping("/materials/{id}/accept")
    public Result<?> acceptMaterial(@PathVariable Long id,
                                    @Valid @RequestBody(required = false) PersonaMaterialDecisionRequest request) {
        return Result.success(service.acceptMaterial(accountId(), id, request == null ? null : request.content()));
    }

    @PostMapping("/materials/{id}/reject")
    public Result<?> rejectMaterial(@PathVariable Long id) {
        service.rejectMaterial(accountId(), id);
        return Result.success();
    }

    @PutMapping("/equipment/{slotCode}")
    public Result<?> updateEquipment(@PathVariable String slotCode,
                                     @Valid @RequestBody PersonaEquipmentRequest request) {
        return Result.success(service.updateEquipment(accountId(), slotCode, request));
    }

    @PutMapping("/equipment/{slotCode}/blueprint")
    public Result<?> updateAccessoryBlueprint(@PathVariable String slotCode,
                                              @RequestBody PersonaAccessoryBlueprintRequest request) {
        return Result.success(service.updateAccessoryBlueprint(accountId(), slotCode, request));
    }

    @PutMapping("/settings")
    public Result<?> updateSettings(@RequestBody PersonaSettingsRequest request) {
        return Result.success(service.updateSettings(accountId(), request));
    }

    private Long accountId() {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId <= 0) throw new BaseException("请先登录");
        return accountId;
    }
}
