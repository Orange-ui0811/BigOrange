package com.orangejd.server.service;

import com.orangejd.pojo.entity.TrendHistory;
import com.orangejd.server.mapper.TrendHistoryMapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TrendHistoryService {
    private final TrendHistoryMapper mapper;
    public TrendHistoryService(TrendHistoryMapper mapper) { this.mapper = mapper; }
    public int insert(TrendHistory value) { return mapper.insert(value); }
    public List<TrendHistory> selectByBrowserId(Long id, int limit) { return mapper.selectByBrowserId(id, limit); }
    public int deleteById(Long id, Long accountId) { return mapper.deleteById(id, accountId); }
    public int deleteByBrowserId(Long accountId) { return mapper.deleteByBrowserId(accountId); }
}
