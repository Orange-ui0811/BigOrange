package com.orangejd.server.controller.admin;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.vo.AccountVO;
import com.orangejd.server.mapper.AccountMapper;
import com.orangejd.server.mapper.UserProfileMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired private AccountMapper accountMapper;
    @Autowired private UserProfileMapper userProfileMapper;

    @GetMapping
    public Result<PageResult<AccountVO>> list(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * com.orangejd.common.util.PageUtil.capSize(size);
        // 简化实现：返回所有账号
        List<AccountVO> records = accountMapper.selectAll(offset, size).stream().map(a -> {
            AccountVO vo = new AccountVO();
            vo.setId(a.getId()); vo.setUsername(a.getUsername());
            vo.setStatus(a.getStatus()); vo.setRole(a.getRole());
            vo.setCreateTime(a.getCreateTime()); return vo;
        }).collect(Collectors.toList());
        int total = accountMapper.countAll();
        return Result.success(new PageResult<>(total, records));
    }

    @PutMapping("/{id}/status")
    public Result<?> toggleStatus(@PathVariable Long id) {
        com.orangejd.pojo.entity.Account account = accountMapper.selectById(id);
        if (account == null) return Result.error("用户不存在");
        String newStatus = "ACTIVE".equals(account.getStatus()) ? "DISABLED" : "ACTIVE";
        accountMapper.updateStatus(id, newStatus);
        return Result.success();
    }
}
