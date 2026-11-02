package com.orangejd.server.service;

import com.orangejd.server.mapper.AccountMapper;
import com.orangejd.server.mapper.OrderMapper;
import com.orangejd.server.mapper.RequirementMapper;
import com.orangejd.server.mapper.WorkMapper;
import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlatformStatisticsService {
    private final AccountMapper accounts; private final WorkMapper works;
    private final RequirementMapper requirements; private final OrderMapper orders;
    public PlatformStatisticsService(AccountMapper accounts, WorkMapper works,
                                     RequirementMapper requirements, OrderMapper orders) {
        this.accounts = accounts; this.works = works; this.requirements = requirements; this.orders = orders;
    }
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalUsers", accounts.countAll()); data.put("totalWorks", works.countAll(""));
        data.put("totalRequirements", requirements.countAll(""));
        data.put("pendingAudits", works.countAll("PENDING_REVIEW") + requirements.countAll("PENDING_REVIEW"));
        data.put("totalOrders", orders.countAllOrders()); return data;
    }
    public List<Map<String, Object>> topWorks(int limit) { return works.selectTopByFavorites(limit); }
    public List<Map<String, Object>> topCreators(int limit) { return accounts.selectTopCreators(limit); }
}
