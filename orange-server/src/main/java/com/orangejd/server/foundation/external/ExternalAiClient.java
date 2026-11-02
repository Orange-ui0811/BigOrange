package com.orangejd.server.foundation.external;

import com.orangejd.common.exception.BaseException;
import com.orangejd.server.foundation.aitool.AiToolProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExternalAiClient {
    private static final Logger log = LoggerFactory.getLogger(ExternalAiClient.class);
    private final AiRuntimeConfigService configService;
    private final RestTemplate restTemplate;

    public ExternalAiClient(AiRuntimeConfigService configService) {
        this.configService = configService;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(120));
        this.restTemplate = new RestTemplate(factory);
    }

    public String callTrend(String configVersion, String systemPrompt, String userPrompt,
                            double temperature, String purpose) {
        return callConfigured(AiRuntimeConfigService.TREND, configVersion,
                systemPrompt, userPrompt, temperature, purpose);
    }

    public String callText(String configVersion, String systemPrompt, String userPrompt,
                           double temperature, String purpose) {
        return call(configService.resolveText(configVersion),
                systemPrompt, userPrompt, temperature, purpose);
    }

    public String callProject(String configVersion, String systemPrompt, String userPrompt,
                              double temperature, String purpose) {
        return callConfigured(AiRuntimeConfigService.PROJECT, configVersion,
                systemPrompt, userPrompt, temperature, purpose);
    }

    public String callContribution(String configVersion, String systemPrompt, String userPrompt,
                                   double temperature, String purpose) {
        return callConfigured(AiRuntimeConfigService.CONTRIBUTION, configVersion,
                systemPrompt, userPrompt, temperature, purpose);
    }

    private String callConfigured(String type, String configVersion,
                                  String systemPrompt, String userPrompt,
                                  double temperature, String purpose) {
        AiRuntimeConfig config = configService.resolve(type, configVersion);
        return call(config, systemPrompt, userPrompt, temperature, purpose);
    }

    @SuppressWarnings("unchecked")
    public String call(AiRuntimeConfig config, String systemPrompt, String userPrompt,
                       double temperature, String purpose) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        body.put("temperature", temperature);
        return call(config, body, purpose);
    }

    @SuppressWarnings("unchecked")
    public String call(AiRuntimeConfig config, Map<String, Object> body, String purpose) {
        if (!StringUtils.hasText(config.endpoint()) || !StringUtils.hasText(config.apiKey())) {
            throw new BaseException("AI 服务尚未配置");
        }
        long started = System.nanoTime();
        String url = config.endpoint().endsWith("/chat/completions")
                ? config.endpoint() : config.endpoint() + "/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.apiKey());
        Map<String, Object> requestBody = new LinkedHashMap<>(body);
        requestBody.put("model", config.model());
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers), Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) throw new BaseException("AI 返回为空");
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices == null || choices.isEmpty()) throw new BaseException("AI 返回缺少 choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = message == null ? null : String.valueOf(message.get("content"));
            if (!StringUtils.hasText(content)) throw new BaseException("AI 返回内容为空");
            log.info("External AI completed purpose={} model={} version={} durationMs={}",
                    purpose, config.model(), config.version(), elapsedMillis(started));
            return stripFence(content);
        } catch (HttpStatusCodeException ex) {
            log.warn("External AI rejected purpose={} status={} durationMs={}",
                    purpose, ex.getStatusCode().value(), elapsedMillis(started));
            throw AiToolProviderException.from(ex);
        } catch (BaseException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("External AI failed purpose={} type={} durationMs={}",
                    purpose, ex.getClass().getSimpleName(), elapsedMillis(started));
            throw new BaseException("AI 服务暂时不可用");
        }
    }

    private String stripFence(String content) {
        return content.replaceFirst("^\\s*```(?:json|markdown)?\\s*", "")
                .replaceFirst("\\s*```\\s*$", "").trim();
    }

    private long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }
}
