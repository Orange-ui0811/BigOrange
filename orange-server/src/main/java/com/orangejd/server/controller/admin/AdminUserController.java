package com.orangejd.server.controller.admin;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.common.context.BaseContext;
import com.orangejd.pojo.vo.AccountVO;
import com.orangejd.server.service.AdminUserAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/users")
@lombok.RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserAccessService accountMapper;

    @GetMapping
    public Result<PageResult<AccountVO>> list(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        size = com.orangejd.common.util.PageUtil.capSize(size);
        int offset = com.orangejd.common.util.PageUtil.offset(page, size);
        // 简化实现：返回所有账号
        List<AccountVO> records = accountMapper.selectAll(offset, size).stream().map(a -> {
            AccountVO vo = new AccountVO();
            vo.setId(a.getId()); vo.setUsername(a.getUsername());
            vo.setEmail(a.getEmail());
            vo.setStatus(a.getStatus()); vo.setRole(a.getRole());
            vo.setCreateTime(a.getCreateTime()); return vo;
        }).collect(Collectors.toList());
        int total = accountMapper.countAll();
        return Result.success(new PageResult<>(total, records));
    }

    @PutMapping("/{id}/status")
    @Transactional
    public Result<?> toggleStatus(@PathVariable Long id) {
        com.orangejd.pojo.entity.Account account = accountMapper.selectById(id);
        if (account == null) return Result.error("用户不存在");
        String newStatus = "ACTIVE".equals(account.getStatus()) ? "DISABLED" : "ACTIVE";
        if ("DISABLED".equals(newStatus) && id.equals(BaseContext.getCurrentId())) {
            return Result.error("不能禁用当前登录的管理员");
        }
        if ("DISABLED".equals(newStatus) && "ADMIN".equals(account.getRole())) {
            List<Long> activeAdminIds = accountMapper.selectActiveAdminIdsForUpdate();
            if (activeAdminIds.size() <= 1) return Result.error("不能禁用唯一可用的管理员");
        }
        accountMapper.updateStatus(id, newStatus);
        return Result.success();
    }
}
