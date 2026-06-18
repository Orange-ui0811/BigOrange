package com.orangejd.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class FeishuNotifyService {

    private static final Logger log = LoggerFactory.getLogger(FeishuNotifyService.class);

    @Value("${orange.feishu.webhook-url:}")
    private String webhookUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /** 审核结果通知 */
    public void sendAuditResult(String title, String author, String action, String reason, boolean degraded) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        boolean approved = "APPROVED".equals(action);
        String color = approved ? "green" : "red";
        String emoji = approved ? "✅" : "❌";
        String status = approved ? "AI 审核通过" : "AI 审核拒绝";
        String degradeNote = degraded ? "\n⚠️ 图片审核失败，已降级为文本审核" : "";

        String markdown = String.format(
            "**作品：**《%s》\n**作者：**%s\n**审核人：**🤖 AI\n**理由：**%s%s",
            title, author, reason, degradeNote);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("msg_type", "interactive");

        Map<String, Object> cardBody = new LinkedHashMap<>();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("template", color);
        Map<String, Object> titleObj = new LinkedHashMap<>();
        titleObj.put("tag", "plain_text");
        titleObj.put("content", emoji + " " + status);
        header.put("title", titleObj);
        cardBody.put("header", header);

        Map<String, Object> element = new LinkedHashMap<>();
        element.put("tag", "markdown");
        element.put("content", markdown);
        cardBody.put("elements", Collections.singletonList(element));
        card.put("card", cardBody);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(card, headers);
            restTemplate.postForEntity(webhookUrl, entity, String.class);
            log.info("飞书通知已发送: {} - {}", title, status);
        } catch (Exception e) {
            log.warn("飞书通知发送失败: {}", e.getMessage().length() > 80 ? e.getMessage().substring(0, 80) : e.getMessage());
        }
    }

    /** 告警通知（降级/失败） */
    public void sendWarning(String title, String author, String message) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("msg_type", "interactive");

        Map<String, Object> cardBody = new LinkedHashMap<>();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("template", "orange");
        Map<String, Object> titleObj = new LinkedHashMap<>();
        titleObj.put("tag", "plain_text");
        titleObj.put("content", "⚠️ AI 审核异常");
        header.put("title", titleObj);
        cardBody.put("header", header);

        Map<String, Object> element = new LinkedHashMap<>();
        element.put("tag", "markdown");
        element.put("content", String.format("**作品：**《%s》\n**作者：**%s\n**原因：**%s", title, author, message));
        cardBody.put("elements", Collections.singletonList(element));
        card.put("card", cardBody);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(card, headers);
            restTemplate.postForEntity(webhookUrl, entity, String.class);
        } catch (Exception e) {
            log.warn("飞书告警发送失败: {}", e.getMessage());
        }
    }
}
