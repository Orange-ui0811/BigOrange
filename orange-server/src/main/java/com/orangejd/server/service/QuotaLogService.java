package com.orangejd.server.service;

import com.orangejd.pojo.entity.QuotaLog;
import com.orangejd.server.mapper.QuotaLogMapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class QuotaLogService {
    private final QuotaLogMapper mapper;
    public QuotaLogService(QuotaLogMapper mapper) { this.mapper = mapper; }
    public List<QuotaLog> selectByAccountId(Long id, int limit) { return mapper.selectByAccountId(id, limit); }
}
