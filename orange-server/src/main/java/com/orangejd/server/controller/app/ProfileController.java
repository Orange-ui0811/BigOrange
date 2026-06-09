package com.orangejd.server.controller.app;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.vo.AccountVO;
import com.orangejd.pojo.vo.UserProfileVO;
import com.orangejd.pojo.vo.WorkVO;
import com.orangejd.server.service.IProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/app/profile")
public class ProfileController {

    @Autowired private IProfileService profileService;

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
