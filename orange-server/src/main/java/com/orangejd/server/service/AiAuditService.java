package com.orangejd.server.service;

import com.orangejd.common.context.BaseContext;
import com.orangejd.pojo.entity.*;
import com.orangejd.server.mapper.*;
import com.orangejd.server.service.impl.AuditServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiAuditService {

    private static final Logger log = LoggerFactory.getLogger(AiAuditService.class);

    @Autowired private WorkMapper workMapper;
    @Autowired private RequirementMapper requirementMapper;
    @Autowired private CategoryMapper categoryMapper;
    @Autowired private AccountMapper accountMapper;
    @Autowired private AuditServiceImpl auditService;
    @Autowired(required = false) private FeishuNotifyService feishuNotifyService;
    @Autowired(required = false) private StringRedisTemplate redisTemplate;

    @Value("${orange.ai-audit.endpoint:}")
    private String apiEndpoint;

    @Value("${orange.ai-audit.api-key:}")
    private String apiKey;

    @Value("${orange.ai-audit.model:deepseek-v4-pro}")
    private String model;

    @Value("${orange.upload.path:}")
    private String uploadPath;

    @PostConstruct
    public void init() {
        // 启动后 10 秒检查：如果开关开着，自动扫描积压
        new Thread(() -> {
            try { Thread.sleep(10000); } catch (Exception ignored) {}
            if (isEnabled()) {
                log.info("AI托管已开启，启动扫描积压");
                auditAllPending();
            }
        }).start();
    }

    // 最多 2 个并发审核
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final RestTemplate restTemplate = new RestTemplate();
    private int runningCount = 0;

    private static final String SYSTEM_PROMPT =
        "你是 OrangeJD 平台的 AI 内容审核员。审核用户提交的作品/需求。\n\n" +
        "审核规则（按优先级）：\n" +
        "1. 【红线-必拒】内容涉及血腥暴力、色情、政治敏感、违法犯罪、不良诱导 → 直接拒绝\n" +
        "2. 【基础质量】标题有意义且长度 ≥ 2 字；简介/描述 ≥ 10 字 → 否则拒绝\n" +
        "3. 【封面图】图片含违规内容 → 拒绝；图片无法判定 → 跳过图片维度\n" +
        "4. 【倾向通过】只要不触犯红线、描述基本完整 → 通过\n\n" +
        "输出严格 JSON（不要额外文字，不要 markdown 代码块）：\n" +
        "{\"action\":\"APPROVED\",\"reason\":\"标题清晰，描述完整，内容合规\"}\n" +
        "或\n" +
        "{\"action\":\"REJECTED\",\"reason\":\"标题过于简单（仅1字），缺乏项目描述\"}";

    public boolean isEnabled() {
        if (redisTemplate == null) return false;
        return "true".equals(redisTemplate.opsForValue().get("system:ai-audit:enabled"));
    }

    public void setEnabled(boolean enabled) {
        if (redisTemplate == null) return;
        redisTemplate.opsForValue().set("system:ai-audit:enabled", String.valueOf(enabled));
    }

    // ---- 公开触发方法 ----

    /** 异步审核单条作品 */
    public void auditWorkAsync(Long workId) {
        if (!isEnabled()) return;
        executor.submit(() -> {
            runningCount++;
            try { auditWork(workId); } catch (Exception e) { log.error("AI审核作品失败 workId={}: {}", workId, e.getMessage()); }
            finally { runningCount--; }
        });
    }

    /** 异步审核单条需求 */
    public void auditRequirementAsync(Long reqId) {
        if (!isEnabled()) return;
        executor.submit(() -> {
            runningCount++;
            try { auditRequirement(reqId); } catch (Exception e) { log.error("AI审核需求失败 reqId={}: {}", reqId, e.getMessage()); }
            finally { runningCount--; }
        });
    }

    /** 批量审核所有待审（返回已触发的数量） */
    public int auditAllPending() {
        int count = 0;
        // 待审作品
        List<Work> works = workMapper.selectAll("PENDING_REVIEW", 0, 500);
        for (Work w : works) { auditWorkAsync(w.getId()); count++; }
        // 待审需求
        List<Requirement> reqs = requirementMapper.selectAll("PENDING_REVIEW", 0, 500);
        for (Requirement r : reqs) { auditRequirementAsync(r.getId()); count++; }
        return count;
    }

    public int getRunningCount() { return runningCount; }

    /** 测试大模型连接 */
    public String testConnection() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 10);
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", "user"); m.put("content", "回复OK");
        messages.add(m);
        body.put("messages", messages);
        return postAi(body);
    }

    // ---- 核心审核逻辑 ----

    private void auditWork(Long workId) {
        Work work = workMapper.selectById(workId);
        if (work == null || !"PENDING_REVIEW".equals(work.getAuditStatus())) return;

        String title = nvl(work.getTitle());
        String summary = nvl(work.getSummary());
        String detail = nvl(work.getDetail());
        if (detail.length() > 3000) detail = detail.substring(0, 3000) + "...";
        String demoUrl = nvl(work.getDemoUrl());
        String coverUrl = buildPublicUrl(work.getCoverUrl());
        String categoryName = "";
        if (work.getCategoryId() != null) {
            Category cat = categoryMapper.selectById(work.getCategoryId());
            if (cat != null) categoryName = cat.getName();
        }
        String authorName = "";
        Account author = accountMapper.selectById(work.getOwnerId());
        if (author != null) authorName = author.getUsername();

        String userPrompt = String.format(
            "请审核以下内容：\n\n标题：%s\n简介：%s\n详情：%s\n分类：%s\n演示链接：%s\n提交者：%s",
            title, summary, detail, categoryName, demoUrl, authorName);

        List<String> detailImages = extractImageUrls(detail);
        AuditResult result = callAi(userPrompt, coverUrl, detailImages);
        applyResult("WORK", workId, work.getOwnerId(), title, authorName, result);
    }

    private void auditRequirement(Long reqId) {
        Requirement req = requirementMapper.selectById(reqId);
        if (req == null || !"PENDING_REVIEW".equals(req.getAuditStatus())) return;

        String title = nvl(req.getTitle());
        String detail = nvl(req.getDetail());
        if (detail.length() > 3000) detail = detail.substring(0, 3000) + "...";
        String categoryName = "";
        if (req.getCategoryId() != null) {
            Category cat = categoryMapper.selectById(req.getCategoryId());
            if (cat != null) categoryName = cat.getName();
        }
        String authorName = "";
        Account author = accountMapper.selectById(req.getPublisherId());
        if (author != null) authorName = author.getUsername();

        String userPrompt = String.format(
            "请审核以下内容：\n\n标题：%s\n详情：%s\n分类：%s\n提交者：%s",
            title, detail, categoryName, authorName);

        List<String> detailImages = extractImageUrls(detail);
        AuditResult result = callAi(userPrompt, null, detailImages);
        applyResult("REQUIREMENT", reqId, req.getPublisherId(), title, authorName, result);
    }

    /** 从 HTML 详情中提取图片 URL */
    private List<String> extractImageUrls(String html) {
        List<String> urls = new ArrayList<>();
        if (html == null) return urls;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(html);
        while (m.find()) {
            String url = m.group(1);
            if (url.startsWith("/")) urls.add(buildPublicUrl(url));
            else if (url.startsWith("http")) urls.add(url);
        }
        return urls;
    }

    // ---- AI 调用 ----

    @SuppressWarnings("unchecked")
    private AuditResult callAi(String userPrompt, String coverUrl, List<String> detailImageUrls) {
        // 组装请求
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.1);
        body.put("max_tokens", 300);

        // system message
        Map<String, String> sysMsg = new LinkedHashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", SYSTEM_PROMPT);

        // user message（多模态格式）
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");

        // 收集所有图片 URL（封面 + 详情内嵌）
        List<String> allImages = new ArrayList<>();
        if (coverUrl != null && !coverUrl.isEmpty()) allImages.add(coverUrl);
        if (detailImageUrls != null) allImages.addAll(detailImageUrls);
        // 去重并限制最多 5 张
        allImages = allImages.stream().distinct().limit(5).collect(java.util.stream.Collectors.toList());

        if (!allImages.isEmpty()) {
            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> textPart = new LinkedHashMap<>();
            textPart.put("type", "text");
            textPart.put("text", userPrompt);
            content.add(textPart);

            for (String imgUrl : allImages) {
                Map<String, Object> imgPart = new LinkedHashMap<>();
                imgPart.put("type", "image_url");
                Map<String, String> imgUrlMap = new LinkedHashMap<>();
                imgUrlMap.put("url", imgUrl);
                imgPart.put("image_url", imgUrlMap);
                content.add(imgPart);
            }

            userMsg.put("content", content);
        } else {
            userMsg.put("content", userPrompt);
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add((Map) sysMsg);
        messages.add((Map) userMsg);
        body.put("messages", messages);

        // 第一次尝试（带图）
        try {
            String response = postAi(body);
            AuditResult result = parseResponse(response);
            result.rawResponse = response;
            return result;
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : "";
            // 图片相关错误 → 降级重试
            if (!allImages.isEmpty() &&
                (errMsg.contains("image") || errMsg.contains("图片") || errMsg.contains("invalid_image"))) {
                log.warn("AI图片审核失败，降级文本审核: {}", errMsg);
                try {
                    // 去掉图片重试
                    userMsg.put("content", userPrompt);
                    messages.set(1, (Map) userMsg);
                    body.put("messages", messages);
                    String response = postAi(body);
                    AuditResult result = parseResponse(response);
                    result.degraded = true;
                    result.rawResponse = response;
                    return result;
                } catch (Exception e2) {
                    return AuditResult.skipped("AI 审核服务异常（降级后仍失败）");
                }
            }
            return AuditResult.skipped("AI 审核服务异常: " + truncate(errMsg, 50));
        }
    }

    @SuppressWarnings("unchecked")
    private String postAi(Map<String, Object> body) {
        String url = apiEndpoint;
        if (!url.startsWith("http")) url = "https://" + url;
        if (!url.endsWith("/chat/completions")) url = url.replaceAll("/$", "") + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        if (resp.getBody() != null) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.getBody().get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                return (String) msg.getOrDefault("content", "");
            }
        }
        throw new RuntimeException("AI 返回为空");
    }

    private AuditResult parseResponse(String content) {
        if (content == null || content.isEmpty()) return AuditResult.skipped("AI 返回为空");
        // 去掉可能的 markdown 代码块
        content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

        try {
            // 尝试解析 JSON
            // 简单 JSON 解析
            String action = null, reason = null;
            Pattern actionP = Pattern.compile("\"action\"\\s*:\\s*\"(\\w+)\"");
            Matcher am = actionP.matcher(content);
            if (am.find()) action = am.group(1);

            Pattern reasonP = Pattern.compile("\"reason\"\\s*:\\s*\"([^\"]+)\"");
            Matcher rm = reasonP.matcher(content);
            if (rm.find()) reason = rm.group(1);

            if (action != null) {
                return new AuditResult(action.equals("APPROVED") ? "APPROVED" : "REJECTED",
                    reason != null ? reason : content);
            }
        } catch (Exception ignored) {}

        // 正则兜底
        if (content.contains("通过") || content.contains("APPROVED") || content.contains("批准")) {
            return new AuditResult("APPROVED", extractReason(content));
        }
        if (content.contains("拒绝") || content.contains("REJECTED") || content.contains("不通过")) {
            return new AuditResult("REJECTED", extractReason(content));
        }

        return AuditResult.skipped("无法解析 AI 返回: " + truncate(content, 80));
    }

    private String extractReason(String text) {
        Pattern p = Pattern.compile("(?:reason|理由|原因)[：:]\\s*(.+?)(?:\\n|$)");
        Matcher m = p.matcher(text);
        if (m.find()) return m.group(1).trim();
        // 取第一行有意义的话
        String[] lines = text.split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.length() > 5 && !line.startsWith("{") && !line.startsWith("```")) return line;
        }
        return text.length() > 100 ? text.substring(0, 100) : text;
    }

    // ---- 执行审核 ----

    private void applyResult(String targetType, Long targetId, Long ownerId,
                             String title, String authorName, AuditResult result) {
        if ("SKIPPED".equals(result.action)) {
            log.warn("AI审核跳过 {}({}): {}", targetType, targetId, result.reason);
            if (feishuNotifyService != null)
                feishuNotifyService.sendWarning(title, authorName, result.reason);
            return;
        }

        boolean approved = "APPROVED".equals(result.action);
        try {
            // 设置 AI 的 context（auditorId=0）
            BaseContext.setCurrentId(0L);
            if ("WORK".equals(targetType)) {
                auditService.auditWork(targetId, approved, (result.degraded ? "[图片降级] " : "") + result.reason);
            } else {
                auditService.auditRequirement(targetId, approved, (result.degraded ? "[图片降级] " : "") + result.reason);
            }

            // 飞书通知
            if (feishuNotifyService != null) {
                feishuNotifyService.sendAuditResult(title, authorName, result.action, result.reason, result.degraded);
            }
            log.info("AI审核完成 {}({}): {} reason={}", targetType, targetId, result.action, result.reason);
        } catch (Exception e) {
            log.error("AI审核执行失败 {}({}): {}", targetType, targetId, e.getMessage());
        } finally {
            BaseContext.clear();
        }
    }

    // ---- 工具方法 ----

    private String buildPublicUrl(String coverUrl) {
        if (coverUrl == null || coverUrl.isEmpty()) return null;
        if (coverUrl.startsWith("http")) return coverUrl;
        // 相对路径 → 公网 URL
        return "http://47.116.8.45" + (coverUrl.startsWith("/") ? "" : "/") + coverUrl;
    }

    private String nvl(String s) { return s == null ? "" : s.trim(); }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    // ---- 内部类 ----

    static class AuditResult {
        String action;    // APPROVED / REJECTED / SKIPPED
        String reason;
        boolean degraded;
        String rawResponse;

        AuditResult(String action, String reason) { this.action = action; this.reason = reason; }
        static AuditResult skipped(String reason) { return new AuditResult("SKIPPED", reason); }
    }
}
