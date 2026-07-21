package com.orangejd.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.mapper.OpenSourceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenSourceTranslationService {

    private static final Logger log = LoggerFactory.getLogger(OpenSourceTranslationService.class);
    private static final int BATCH_SIZE = 10;

    private final OpenSourceMapper mapper;
    private final OpenSourceAiClient aiClient;
    private final ObjectMapper objectMapper;

    public OpenSourceTranslationService(OpenSourceMapper mapper, OpenSourceAiClient aiClient,
                                        ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    public int translatePending(int limit) {
        List<Map<String, Object>> pending = mapper.selectTranslationPending(Math.max(1, Math.min(limit, 100)));
        int translated = 0;
        for (int start = 0; start < pending.size(); start += BATCH_SIZE) {
            List<Map<String, Object>> batch = pending.subList(start, Math.min(start + BATCH_SIZE, pending.size()));
            translated += translateBatch(batch);
        }
        return translated;
    }

    public void translateOne(Long issueId) {
        Map<String, Object> issue = mapper.selectIssueById(issueId);
        if (issue == null) throw new com.orangejd.common.exception.BaseException("Issue 不存在");
        translateBatch(List.of(issue));
    }

    private int translateBatch(List<Map<String, Object>> batch) {
        StringBuilder input = new StringBuilder();
        for (Map<String, Object> issue : batch) {
            String body = String.valueOf(issue.getOrDefault("bodyOriginal", ""));
            if (body.length() > 1_600) body = body.substring(0, 1_600);
            input.append("\nID: ").append(issue.get("id"))
                    .append("\nTitle: ").append(issue.get("titleOriginal"))
                    .append("\nBody: ").append(body).append("\n");
        }

        try {
            String content = aiClient.call(
                    "你是开源项目内容编辑。忠实翻译，不补充原文没有的实现结论。",
                    "请把以下 GitHub Issue 转为简体中文。每条只输出中文标题和一句不超过90字的中文摘要。"
                            + "严格输出 JSON：{\"items\":[{\"id\":1,\"titleZh\":\"\",\"summaryZh\":\"\"}]}。\n"
                            + input,
                    0.1);
            JsonNode items = objectMapper.readTree(content).path("items");
            Map<Long, JsonNode> byId = new HashMap<>();
            if (items.isArray()) items.forEach(item -> byId.put(item.path("id").asLong(), item));

            int success = 0;
            for (Map<String, Object> issue : batch) {
                long id = ((Number) issue.get("id")).longValue();
                JsonNode item = byId.get(id);
                if (item == null || item.path("titleZh").asText("").isBlank()
                        || item.path("summaryZh").asText("").isBlank()) {
                    mapper.updateIssueTranslation(id, null, null, "FAILED");
                    continue;
                }
                mapper.updateIssueTranslation(id, abbreviate(item.path("titleZh").asText(), 500),
                        abbreviate(item.path("summaryZh").asText(), 900), "DONE");
                success++;
            }
            return success;
        } catch (Exception e) {
            List<Long> failedIds = new ArrayList<>();
            for (Map<String, Object> issue : batch) {
                long id = ((Number) issue.get("id")).longValue();
                failedIds.add(id);
                mapper.updateIssueTranslation(id, null, null, "FAILED");
            }
            log.warn("Issue 翻译批次失败 ids={}, reason={}", failedIds, e.getMessage());
            return 0;
        }
    }

    private String abbreviate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
