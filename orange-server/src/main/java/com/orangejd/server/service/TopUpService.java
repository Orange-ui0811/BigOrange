package com.orangejd.server.service;

import com.orangejd.pojo.entity.TopUp;
import com.orangejd.server.mapper.TopUpMapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TopUpService {
    private final TopUpMapper mapper;
    public TopUpService(TopUpMapper mapper) { this.mapper = mapper; }
    public int insert(TopUp value) { return mapper.insert(value); }
    public List<TopUp> selectAll(int offset, int limit) { return mapper.selectAll(offset, limit); }
}
