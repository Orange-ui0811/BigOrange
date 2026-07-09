package com.orangejd.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class TurnstileService {

    private static final Logger log = LoggerFactory.getLogger(TurnstileService.class);
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    @Value("${orange.turnstile.secret-key:}")
    private String secretKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean verify(String token) {
        if (secretKey == null || secretKey.isEmpty()) {
            log.warn("Turnstile secret key 未配置，跳过验证");
            return true; // 未配置时放行，方便开发调试
        }
        if (token == null || token.isEmpty()) {
            return false;
        }
        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("secret", secretKey);
            body.add("response", token);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(VERIFY_URL, entity, Map.class);

            if (response.getBody() != null && Boolean.TRUE.equals(response.getBody().get("success"))) {
                return true;
            }
            log.warn("Turnstile 验证失败: {}", response.getBody());
            return false;
        } catch (Exception e) {
            log.error("Turnstile 验证异常: {}", e.getMessage());
            return false;
        }
    }
}
