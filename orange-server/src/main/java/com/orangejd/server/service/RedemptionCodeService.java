package com.orangejd.server.service;

import com.orangejd.pojo.entity.RedemptionCode;
import com.orangejd.server.mapper.RedemptionCodeMapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RedemptionCodeService {
    private final RedemptionCodeMapper mapper;
    public RedemptionCodeService(RedemptionCodeMapper mapper) { this.mapper = mapper; }
    public int insert(RedemptionCode value) { return mapper.insert(value); }
    public List<RedemptionCode> selectAll(int offset, int size) { return mapper.selectAll(offset, size); }
    public long countAll() { return mapper.countAll(); }
}
