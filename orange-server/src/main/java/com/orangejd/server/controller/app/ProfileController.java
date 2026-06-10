package com.orangejd.server.controller.app;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.vo.AccountVO;
import com.orangejd.pojo.vo.UserProfileVO;
import com.orangejd.pojo.vo.WorkVO;
import com.orangejd.common.context.BaseContext;
import com.orangejd.server.service.IProfileService;
import com.orangejd.server.service.ProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/app/profile")
public class ProfileController {

    @Autowired private IProfileService profileService;

    @GetMapping("/me-info")
    public Result<Map<String, Object>> meInfo() {
        Long id = com.orangejd.common.context.BaseContext.getCurrentId();
        AccountVO account = profileService.getAuthorProfile(id);
        UserProfileVO detail = profileService.getAuthorProfileDetail(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("username", account != null ? account.getUsername() : "");
        result.put("nickname", detail != null && detail.getNickname() != null ? detail.getNickname() : (account != null ? account.getUsername() : ""));
        return Result.success(result);
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> myProfile() {
        Long id = BaseContext.getCurrentId();
        UserProfileVO detail = profileService.getAuthorProfileDetail(id);
        Map<String, Object> result = new LinkedHashMap<>();
        if (detail != null) {
            result.put("nickname", detail.getNickname());
            result.put("bio", detail.getBio());
            result.put("skills", detail.getSkills());
            result.put("contactIntro", detail.getContactIntro());
            result.put("avatarUrl", detail.getAvatarUrl());
        }
        return Result.success(result);
    }

    @PutMapping("/update")
    public Result<?> updateProfile(@RequestBody java.util.Map<String, String> body) {
        ((ProfileService)profileService).updateProfile(body);
        return Result.success();
    }

    @GetMapping("/{accountId}")
    public Result<Map<String, Object>> profile(@PathVariable Long accountId,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        AccountVO account = profileService.getAuthorProfile(accountId);
        if (account == null) return Result.success(null);
        UserProfileVO detail = profileService.getAuthorProfileDetail(accountId);
        PageResult<WorkVO> works = profileService.getAuthorWorks(accountId, page, size);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("account", account);
        result.put("profile", detail);
        result.put("works", works);
        return Result.success(result);
    }
}
