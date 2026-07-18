package com.orangejd.server.service.impl;

import com.orangejd.common.result.PageResult;
import com.orangejd.server.mapper.AccountMapper;
import com.orangejd.server.mapper.CategoryMapper;
import com.orangejd.server.mapper.LeaderboardSnapshotMapper;
import com.orangejd.server.mapper.WorkMapper;
import com.orangejd.pojo.entity.LeaderboardSnapshot;
import com.orangejd.server.service.LeaderboardService;
import com.orangejd.pojo.entity.Category;
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
public class LeaderboardServiceImpl implements LeaderboardService {

    @Autowired private WorkMapper workMapper;
    @Autowired private AccountMapper accountMapper;
    @Autowired private CategoryMapper categoryMapper;
    @Autowired private LeaderboardSnapshotMapper snapshotMapper;
    @Autowired(required = false) private StringRedisTemplate redisTemplate;

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

        // 尝试读今天的快照
        LeaderboardSnapshot snap = snapshotMapper.selectByKey("github", topicName, period, today);
        String warning = null;

        // 今天没有时回退到最近一次成功快照，避免定时抓取或 GitHub 临时故障让榜单消失。
        if (snap == null) {
            snap = snapshotMapper.selectLatest("github", topicName, period);
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

    // 简单 JSON 序列化（避免引入额外依赖）
    private String toJson(PageResult<Map<String, Object>> result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"total\":").append(result.getTotal()).append(",\"records\":[");
        boolean first = true;
        for (Map<String, Object> m : result.getRecords()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{");
            boolean firstField = true;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                if (!firstField) sb.append(",");
                firstField = false;
                sb.append("\"").append(e.getKey()).append("\":");
                Object v = e.getValue();
                if (v == null) sb.append("null");
                else if (v instanceof Number) sb.append(v);
                else sb.append("\"").append(v.toString().replace("\"", "\\\"").replace("\n", "\\n")).append("\"");
            }
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private PageResult<Map<String, Object>> parsePageResult(String json) {
        // 简化的 JSON 解析
        try {
            int totalStart = json.indexOf("\"total\":") + 8;
            int totalEnd = json.indexOf(",", totalStart);
            long total = Long.parseLong(json.substring(totalStart, totalEnd).trim());

            List<Map<String, Object>> records = new ArrayList<>();
            int arrStart = json.indexOf("\"records\":[") + 11;
            int arrEnd = json.lastIndexOf("]}");
            if (arrEnd < 0) arrEnd = json.length() - 2;
            String arrStr = json.substring(arrStart, arrEnd);

            // 解析对象数组
            int depth = 0, objStart = -1;
            for (int i = 0; i < arrStr.length(); i++) {
                if (arrStr.charAt(i) == '{') {
                    if (depth == 0) objStart = i;
                    depth++;
                } else if (arrStr.charAt(i) == '}') {
                    depth--;
                    if (depth == 0 && objStart >= 0) {
                        String objStr = arrStr.substring(objStart + 1, i);
                        Map<String, Object> map = new LinkedHashMap<>();
                        String[] pairs = objStr.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                        for (String pair : pairs) {
                            int colon = pair.indexOf(":");
                            if (colon < 0) continue;
                            String key = pair.substring(0, colon).trim().replaceAll("^\"|\"$", "");
                            String val = pair.substring(colon + 1).trim().replaceAll("^\"|\"$", "");
                            if (val.matches("-?\\d+(\\.\\d+)?")) {
                                if (val.contains(".")) map.put(key, Double.parseDouble(val));
                                else map.put(key, Long.parseLong(val));
                            } else if ("null".equals(val)) {
                                map.put(key, null);
                            } else {
                                map.put(key, val.replace("\\\"", "\"").replace("\\n", "\n"));
                            }
                        }
                        records.add(map);
                    }
                }
            }
            return new PageResult<>(total, records);
        } catch (Exception e) {
            return new PageResult<>(0L, Collections.emptyList());
        }
    }
}
