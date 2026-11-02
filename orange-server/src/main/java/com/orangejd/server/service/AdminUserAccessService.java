package com.orangejd.server.service;

import com.orangejd.pojo.entity.Account;
import com.orangejd.server.mapper.AccountMapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AdminUserAccessService {
    private final AccountMapper mapper;
    public AdminUserAccessService(AccountMapper mapper) { this.mapper = mapper; }
    public List<Account> selectAll(int offset, int limit) { return mapper.selectAll(offset, limit); }
    public int countAll() { return mapper.countAll(); }
    public Account selectById(Long id) { return mapper.selectById(id); }
    public List<Long> selectActiveAdminIdsForUpdate() { return mapper.selectActiveAdminIdsForUpdate(); }
    public int updateStatus(Long id, String status) { return mapper.updateStatus(id, status); }
}
