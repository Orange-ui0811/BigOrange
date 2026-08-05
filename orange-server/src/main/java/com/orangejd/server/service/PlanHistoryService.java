package com.orangejd.server.service;

import com.orangejd.server.mapper.PlanHistoryMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PlanHistoryService {
    private final PlanHistoryMapper mapper;
    public PlanHistoryService(PlanHistoryMapper mapper) { this.mapper = mapper; }
    public List<Map<String, Object>> selectByAccountId(Long id, int limit) { return mapper.selectByAccountId(id, limit); }
    public int deleteById(Long id, Long accountId) { return mapper.deleteById(id, accountId); }
    public int deleteByAccountId(Long accountId) { return mapper.deleteByAccountId(accountId); }
    public int updateOwned(Map<String, Object> record) { return mapper.updateOwned(record); }
    public int insert(Map<String, Object> record) { return mapper.insert(record); }
}
