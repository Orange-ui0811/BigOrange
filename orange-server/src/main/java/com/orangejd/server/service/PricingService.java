package com.orangejd.server.service;

import com.orangejd.pojo.entity.AiServicePricing;
import com.orangejd.pojo.entity.PricingChangeLog;
import com.orangejd.server.mapper.PricingMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PricingService {

    public static final String REWARD_WORK_APPROVED = "reward:work-approved";
    public static final String REWARD_JOB_APPROVED = "reward:job-approved";
    public static final String REWARD_COMPETITION_APPROVED = "reward:competition-approved";

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);

    private static final Map<String, Integer> DEFAULTS = Map.ofEntries(
            Map.entry("ai:trend:v1", 50),
            Map.entry("ai:trend:v2", 50),
            Map.entry("ai:plan", 40),
            Map.entry("ai:match", 50),
            Map.entry("ai:launch-check", 40),
            Map.entry("ai:open-source-match", 40),
            Map.entry("ai:open-source-job-match", 40),
            Map.entry("ai:open-source-guide", 50),
            Map.entry(REWARD_WORK_APPROVED, 50),
            Map.entry(REWARD_JOB_APPROVED, 50),
            Map.entry(REWARD_COMPETITION_APPROVED, 20)
    );

    private static final Map<String, String> SERVICE_NAMES = Map.ofEntries(
            Map.entry("ai:trend:v1", "趋势分析"),
            Map.entry("ai:trend:v2", "深度趋势分析"),
            Map.entry("ai:plan", "方案设计"),
            Map.entry("ai:match", "岗位匹配"),
            Map.entry("ai:launch-check", "上线体检"),
            Map.entry("ai:open-source-match", "开源 Issue AI 匹配"),
            Map.entry("ai:open-source-job-match", "开源 Issue 岗位匹配"),
            Map.entry("ai:open-source-guide", "开源贡献指南"),
            Map.entry(REWARD_WORK_APPROVED, "作品审核通过奖励"),
            Map.entry(REWARD_JOB_APPROVED, "岗位投稿审核通过奖励"),
            Map.entry(REWARD_COMPETITION_APPROVED, "比赛投稿审核通过奖励")
    );

    private final PricingMapper pricingMapper;
    private final Map<String, Integer> costCache = new ConcurrentHashMap<>();
    private final Map<String, String> nameCache = new ConcurrentHashMap<>();

    public PricingService(PricingMapper pricingMapper) {
        this.pricingMapper = pricingMapper;
    }

    @PostConstruct
    public void init() {
        loadFromDB();
    }

    public synchronized void loadFromDB() {
        costCache.clear();
        nameCache.clear();
        DEFAULTS.forEach(costCache::put);
        SERVICE_NAMES.forEach(nameCache::put);
        try {
            for (AiServicePricing pricing : pricingMapper.selectAll()) {
                costCache.put(pricing.getServiceKey(), pricing.getSegmentCost());
                nameCache.put(pricing.getServiceKey(), pricing.getServiceName());
            }
        } catch (Exception exception) {
            log.error("[pricing] Failed to load pricing, using defaults: {}", exception.getMessage());
        }
    }

    public int getCost(String serviceKey) {
        Integer cost = costCache.get(serviceKey);
        if (cost != null) {
            return cost;
        }
        Integer fallback = DEFAULTS.getOrDefault(serviceKey, 50);
        log.warn("[pricing] Missing {}, using fallback {}", serviceKey, fallback);
        return fallback;
    }

    public int getReward(String serviceKey) {
        if (!isRewardKey(serviceKey)) {
            throw new IllegalArgumentException("不是奖励定价项: " + serviceKey);
        }
        return getCost(serviceKey);
    }

    public List<AiServicePricing> listAll() {
        return pricingMapper.selectAll();
    }

    public List<PricingChangeLog> getRecentLogs(int limit) {
        return pricingMapper.selectRecentLogs(limit);
    }

    @Transactional
    public int updateCost(String serviceKey, int newCost, Long adminId, String ignoredServiceName) {
        if (!DEFAULTS.containsKey(serviceKey)) {
            throw new IllegalArgumentException("未知定价项: " + serviceKey);
        }
        int minimum = isRewardKey(serviceKey) ? 0 : 1;
        if (newCost < minimum || newCost > 10000) {
            throw new IllegalArgumentException(
                    isRewardKey(serviceKey)
                            ? "奖励数量必须在 0-10000 之间"
                            : "消耗数量必须在 1-10000 之间"
            );
        }

        int oldCost = getCost(serviceKey);
        String serviceName = SERVICE_NAMES.get(serviceKey);

        AiServicePricing pricing = new AiServicePricing();
        pricing.setServiceKey(serviceKey);
        pricing.setServiceName(serviceName);
        pricing.setSegmentCost(newCost);
        pricing.setUpdatedBy(adminId);
        pricingMapper.upsert(pricing);

        PricingChangeLog logEntry = new PricingChangeLog();
        logEntry.setServiceKey(serviceKey);
        logEntry.setOldCost(oldCost);
        logEntry.setNewCost(newCost);
        logEntry.setAdminId(adminId);
        pricingMapper.insertChangeLog(logEntry);

        costCache.put(serviceKey, newCost);
        nameCache.put(serviceKey, serviceName);
        log.info("[pricing] adminId={} updated {}: {} -> {}", adminId, serviceKey, oldCost, newCost);
        return newCost;
    }

    private boolean isRewardKey(String serviceKey) {
        return serviceKey != null && serviceKey.startsWith("reward:");
    }
}
