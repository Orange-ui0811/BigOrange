package com.orangejd.server.service.impl;

import com.orangejd.common.result.PageResult;
import com.orangejd.server.mapper.AccountMapper;
import com.orangejd.server.mapper.CategoryMapper;
import com.orangejd.server.mapper.LeaderboardSnapshotMapper;
import com.orangejd.server.mapper.WorkMapper;
import com.orangejd.pojo.entity.LeaderboardSnapshot;
import com.orangejd.server.service.LeaderboardService;
import com.orangejd.pojo.entity.Category;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@lombok.RequiredArgsConstructor
public class LeaderboardServiceImpl implements LeaderboardService {

    private final WorkMapper workMapper;
    private final AccountMapper accountMapper;
    private final CategoryMapper categoryMapper;
    private final LeaderboardSnapshotMapper snapshotMapper;
    private final @org.springframework.lang.Nullable StringRedisTemplate redisTemplate;
    private final @org.springframework.lang.Nullable ObjectMapper objectMapper = new ObjectMapper();

    @Value("${orange.leaderboard.local-cache-ttl-minutes:15}")
    private int localCacheTtl;

    private String getWeekStart() {
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // ==================== 本网站作品榜 ====================

    @Override
    public PageResult<Map<String, Object>> getWorks(Long categoryId, String period, int page, int size) {
        size = com.orangejd.common.util.PageUtil.capSize(size);
        page = com.orangejd.common.util.PageUtil.safePage(page);
        int limit = size;

        String cacheKey = "leaderboard:local:works:" + (categoryId == null ? "all" : categoryId) + ":" + period + ":p" + page;
        String cached = getCache(cacheKey);
        if (cached != null) {
            return parsePageResult(cached);
        }

        List<Map<String, Object>> list;
        int total;
        boolean isWeek = "week".equals(period);
        if (isWeek) {
            list = workMapper.selectLeaderboardWorksWeekly(categoryId, getWeekStart(), limit);
            total = workMapper.countLeaderboardWorksWeekly(categoryId, getWeekStart());
        } else {
            list = workMapper.selectLeaderboardWorks(categoryId, limit);
            total = workMapper.countLeaderboardWorks(categoryId);
        }

        PageResult<Map<String, Object>> result = new PageResult<>((long) total, list);
        setCache(cacheKey, toJson(result), localCacheTtl);
        return result;
    }

    // ==================== 本网站创作者榜 ====================

    @Override
    public PageResult<Map<String, Object>> getCreators(String period, int page, int size) {
        size = com.orangejd.common.util.PageUtil.capSize(size);
        page = com.orangejd.common.util.PageUtil.safePage(page);
        int limit = size;

        String cacheKey = "leaderboard:local:creators:" + period + ":p" + page;
        String cached = getCache(cacheKey);
        if (cached != null) {
            return parsePageResult(cached);
        }

        List<Map<String, Object>> list;
        int total;
        boolean isWeek = "week".equals(period);
        if (isWeek) {
            list = accountMapper.selectLeaderboardCreatorsWeekly(getWeekStart(), limit);
            total = accountMapper.countLeaderboardCreatorsWeekly(getWeekStart());
        } else {
            list = accountMapper.selectLeaderboardCreators(limit);
            total = accountMapper.countLeaderboardCreators();
        }

        PageResult<Map<String, Object>> result = new PageResult<>((long) total, list);
        setCache(cacheKey, toJson(result), localCacheTtl);
        return result;
    }

    // ==================== GitHub 仓库榜 ====================

    // ==================== GitHub 仓库榜（从数据库日更快照读取）====================

    @Override
    public Map<String, Object> getGithubRepos(String topicName, String period, int page, int size) {
        size = com.orangejd.common.util.PageUtil.capSize(size);
        page = com.orangejd.common.util.PageUtil.safePage(page);
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        String stableTopic = topicName == null || topicName.isBlank() ? "__ALL__" : topicName;
        // 尝试读今天的快照；聚合榜单优先使用稳定键。
        LeaderboardSnapshot snap = snapshotMapper.selectByKey("github", stableTopic, period, today);
        if (snap == null && "__ALL__".equals(stableTopic)) {
            snap = snapshotMapper.selectByKey("github", null, period, today);
        }
        String warning = null;

        // 今天没有时回退到最近一次成功快照，避免定时抓取或 GitHub 临时故障让榜单消失。
        if (snap == null) {
            snap = snapshotMapper.selectLatest("github", stableTopic, period);
            if (snap == null && "__ALL__".equals(stableTopic)) {
                snap = snapshotMapper.selectLatest("github", null, period);
            }
            if (snap != null) {
                warning = "榜单数据更新中，当前展示 " + snap.getSnapshotDate() + " 的最近快照";
            }
        }

        if (snap != null && snap.getDataJson() != null) {
            PageResult<Map<String, Object>> cached = parsePageResult(snap.getDataJson());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", cached.getTotal());
            result.put("records", cached.getRecords());
            if (warning != null) result.put("warning", warning);
            return result;
        }

        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("total", 0);
        empty.put("records", Collections.emptyList());
        empty.put("warning", "GitHub 榜单数据尚未生成，请稍后重试");
        return empty;
    }

    // ==================== 缓存工具 ====================

    private String getCache(String key) {
        if (redisTemplate == null) return null;
        try { return redisTemplate.opsForValue().get(key); } catch (Exception e) { return null; }
    }

    private void setCache(String key, String value, int ttlMinutes) {
        if (redisTemplate == null) return;
        try { redisTemplate.opsForValue().set(key, value, ttlMinutes, TimeUnit.MINUTES); } catch (Exception e) { }
    }

    private String toJson(PageResult<Map<String, Object>> result) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "total", result.getTotal(),
                    "records", result.getRecords()));
        } catch (Exception ex) {
            throw new IllegalArgumentException("榜单缓存序列化失败", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private PageResult<Map<String, Object>> parsePageResult(String json) {
        try {
            Map<String, Object> data = objectMapper.readValue(json, new TypeReference<>() {});
            long total = data.get("total") instanceof Number number ? number.longValue() : 0L;
            List<Map<String, Object>> records = objectMapper.convertValue(
                    data.getOrDefault("records", Collections.emptyList()), new TypeReference<>() {});
            return new PageResult<>(total, records);
        } catch (Exception e) {
            return new PageResult<>(0L, Collections.emptyList());
        }
    }
}
