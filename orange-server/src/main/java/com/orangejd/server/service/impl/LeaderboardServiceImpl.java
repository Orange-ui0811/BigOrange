package com.orangejd.server.service.impl;

import com.orangejd.common.result.PageResult;
import com.orangejd.server.mapper.AccountMapper;
import com.orangejd.server.mapper.CategoryMapper;
import com.orangejd.server.mapper.WorkMapper;
import com.orangejd.server.service.ILeaderboardService;
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
public class LeaderboardServiceImpl implements ILeaderboardService {

    @Autowired private WorkMapper workMapper;
    @Autowired private AccountMapper accountMapper;
    @Autowired private CategoryMapper categoryMapper;
    @Autowired(required = false) private StringRedisTemplate redisTemplate;

    @Value("${orange.leaderboard.github-token:}")
    private String githubToken;

    @Value("${orange.leaderboard.github-cache-ttl-minutes:30}")
    private int githubCacheTtl;

    @Value("${orange.leaderboard.local-cache-ttl-minutes:15}")
    private int localCacheTtl;

    @Value("${orange.leaderboard.default-top-n:20}")
    private int defaultTopN;

    private final RestTemplate restTemplate = new RestTemplate();

    private String getWeekStart() {
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // ==================== 本网站作品榜 ====================

    @Override
    public PageResult<Map<String, Object>> getWorks(Long categoryId, String period, int page, int size) {
        size = Math.min(size, 50);
        int limit = size;
        int offset = (page - 1) * size;

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
        size = Math.min(size, 50);
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

    @Override
    public Map<String, Object> getGithubRepos(String topicName, String period, int page, int size) {
        size = Math.min(size, 50);

        String cacheKey = "leaderboard:github:" + (topicName == null ? "all" : topicName) + ":" + period + ":p" + page;

        try {
            // 构建 GitHub Search API URL
            StringBuilder q = new StringBuilder();
            if (topicName != null && !topicName.isEmpty()) {
                q.append("topic:").append(topicName).append(" ");
            }
            if ("week".equals(period)) {
                String isoDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .format(DateTimeFormatter.ISO_LOCAL_DATE);
                q.append("created:>").append(isoDate);
            } else {
                q.append("stars:>10");
            }

            String url = "https://api.github.com/search/repositories?q=" + q.toString().trim()
                    + "&sort=stars&order=desc&per_page=" + size + "&page=" + page;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/vnd.github.v3+json");
            if (githubToken != null && !githubToken.isEmpty()) {
                headers.set("Authorization", "Bearer " + githubToken);
            }
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("items")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
                int totalCount = (int) response.getBody().getOrDefault("total_count", 0);

                List<Map<String, Object>> records = items.stream().map(item -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("id", item.get("id"));
                    r.put("name", item.get("name"));
                    r.put("fullName", item.get("full_name"));
                    r.put("description", item.getOrDefault("description", ""));
                    r.put("htmlUrl", item.get("html_url"));
                    r.put("language", item.getOrDefault("language", ""));
                    r.put("stars", item.get("stargazers_count"));
                    r.put("forks", item.get("forks_count"));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> owner = (Map<String, Object>) item.get("owner");
                    r.put("ownerName", owner != null ? owner.get("login") : "");
                    r.put("ownerAvatar", owner != null ? owner.get("avatar_url") : "");
                    return r;
                }).collect(Collectors.toList());

                String json = toJson(new PageResult<>((long) totalCount, records));
                setCache(cacheKey, json, githubCacheTtl);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("total", totalCount);
                result.put("records", records);
                return result;
            }
        } catch (Exception e) {
            // 降级: 尝试返回缓存数据
            String cached = getCache(cacheKey);
            if (cached != null) {
                PageResult<Map<String, Object>> cachedResult = parsePageResult(cached);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("total", cachedResult.getTotal());
                result.put("records", cachedResult.getRecords());
                result.put("warning", "GitHub API 暂时不可用，展示缓存数据");
                return result;
            }
        }

        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("total", 0);
        empty.put("records", Collections.emptyList());
        empty.put("warning", "GitHub API 暂时不可用，请稍后重试");
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
