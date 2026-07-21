package com.orangejd.server.service;

import com.orangejd.pojo.entity.AiServicePricing;
import com.orangejd.pojo.entity.PricingChangeLog;
import com.orangejd.server.mapper.PricingMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 定价管理服务
 * MySQL 主存储 + 内存缓存，启动时加载
 */
@Service
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);

    @Autowired
    private PricingMapper pricingMapper;

    /** 内存缓存：serviceKey → segmentCost */
    private final Map<String, Integer> costCache = new ConcurrentHashMap<>();

    /** 内存缓存：serviceKey → serviceName */
    private final Map<String, String> nameCache = new ConcurrentHashMap<>();

    /** 默认定价（兜底，防止数据库无数据） */
    private static final Map<String, Integer> DEFAULTS = Map.of(
        "ai:trend:v1", 50,
        "ai:trend:v2", 50,
        "ai:plan", 40,
        "ai:match", 50,
        "ai:launch-check", 40,
        "ai:open-source-match", 40,
        "ai:open-source-job-match", 40,
        "ai:open-source-guide", 50
    );

    @PostConstruct
    public void init() {
        loadFromDB();
    }

    /** 从 MySQL 加载全部定价到内存缓存 */
    public synchronized void loadFromDB() {
        try {
            List<AiServicePricing> all = pricingMapper.selectAll();
            if (all.isEmpty()) {
                log.info("[定价] 数据库无记录，使用硬编码默认值");
                DEFAULTS.forEach((k, v) -> costCache.put(k, v));
            } else {
                for (AiServicePricing p : all) {
                    costCache.put(p.getServiceKey(), p.getSegmentCost());
                    nameCache.put(p.getServiceKey(), p.getServiceName());
                    log.info("[定价] 加载: {} = {} segments", p.getServiceKey(), p.getSegmentCost());
                }
            }
        } catch (Exception e) {
            log.error("[定价] 数据库加载失败，使用默认值兜底: {}", e.getMessage());
            DEFAULTS.forEach((k, v) -> costCache.putIfAbsent(k, v));
        }
    }

    /**
     * 获取服务消费金额
     * @param serviceKey 如 ai:trend:v1
     * @return 消费金额（segments），未找到返回默认值
     */
    public int getCost(String serviceKey) {
        Integer cost = costCache.get(serviceKey);
        if (cost != null) return cost;
        // 兜底
        Integer fallback = DEFAULTS.getOrDefault(serviceKey, 50);
        log.warn("[定价] {} 未配置，使用兜底值 {}", serviceKey, fallback);
        return fallback;
    }

    /** 获取所有定价信息 */
    public List<AiServicePricing> listAll() {
        return pricingMapper.selectAll();
    }

    /** 获取最近 N 条变更日志 */
    public List<PricingChangeLog> getRecentLogs(int limit) {
        return pricingMapper.selectRecentLogs(limit);
    }

    /**
     * 更新定价并记录审计日志
     * @param adminId 操作管理员 ID
     * @return 更新后的 cost
     */
    @Transactional
    public int updateCost(String serviceKey, int newCost, Long adminId, String serviceName) {
        // 校验范围
        if (newCost < 1 || newCost > 10000) {
            throw new IllegalArgumentException("定价范围：1 ~ 10000 segments");
        }

        int oldCost = costCache.getOrDefault(serviceKey, DEFAULTS.getOrDefault(serviceKey, 50));

        // 写 MySQL
        AiServicePricing pricing = new AiServicePricing();
        pricing.setServiceKey(serviceKey);
        pricing.setServiceName(serviceName);
        pricing.setSegmentCost(newCost);
        pricing.setUpdatedBy(adminId);
        pricingMapper.upsert(pricing);

        // 写审计日志
        PricingChangeLog logEntry = new PricingChangeLog();
        logEntry.setServiceKey(serviceKey);
        logEntry.setOldCost(oldCost);
        logEntry.setNewCost(newCost);
        logEntry.setAdminId(adminId);
        pricingMapper.insertChangeLog(logEntry);

        // 刷新内存缓存
        costCache.put(serviceKey, newCost);
        if (serviceName != null) nameCache.put(serviceKey, serviceName);

        log.info("[定价] adminId={} 修改 {}: {} → {}", adminId, serviceKey, oldCost, newCost);
        return newCost;
    }
}
