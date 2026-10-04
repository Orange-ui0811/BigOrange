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
import java.util.Objects;

@Component
public class ExternalAiClient {
    private static final Logger log = LoggerFactory.getLogger(ExternalAiClient.class);
    private final AiRuntimeConfigService configService;
    private final RestTemplate restTemplate;
    private final String customerModel;
    private final boolean customerThinking;

    public ExternalAiClient(AiRuntimeConfigService configService) {
        this(configService, "");
    }

    public ExternalAiClient(AiRuntimeConfigService configService, String customerModel) {
        this(configService,customerModel,false);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ExternalAiClient(AiRuntimeConfigService configService,
                            @org.springframework.beans.factory.annotation.Value("${orange.customer.model:deepseek-flash}") String customerModel,
                            @org.springframework.beans.factory.annotation.Value("${orange.customer.thinking:false}") boolean customerThinking) {
        this.configService = configService;
        this.customerModel = customerModel == null ? "" : customerModel.trim();
        this.customerThinking=customerThinking;
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

    /** Preserve provider usage for RAG experiments without changing request behavior. */
    public TextResult callTextMeasured(String configVersion, String systemPrompt, String userPrompt,
                                       double temperature, String purpose) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", List.of(Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        body.put("temperature", temperature);
        return callMeasured(configService.resolveText(configVersion), body, purpose);
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
        return callMeasured(config, body, purpose).text();
    }

    @SuppressWarnings("unchecked")
    private TextResult callMeasured(AiRuntimeConfig config, Map<String, Object> body, String purpose) {
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
            Object rawUsage = responseBody.get("usage");
            Map<String, Object> usage = rawUsage instanceof Map<?, ?> values
                    ? java.util.Collections.unmodifiableMap(new LinkedHashMap<>((Map<String, Object>) values)) : Map.of();
            return new TextResult(stripFence(content), config.model(), usage, elapsedMillis(started));
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

    public record TextResult(String text, String model, Map<String, Object> usage, long durationMs, String finishReason) {
        public TextResult(String text,String model,Map<String,Object> usage,long durationMs) { this(text,model,usage,durationMs,"unknown"); }
    }

    /** Customer path has its own bounded transport; other callers keep their existing timeout. */
    public TextResult callCustomer(String system, String user, int seconds, int maxTokens) {
        return customerRequest(system,user,seconds,maxTokens,false,customerThinking);
    }
    public TextResult callCustomer(String system, String user, int seconds, int maxTokens, boolean thinking) {
        return customerRequest(system,user,seconds,maxTokens,false,thinking);
    }
    public TextResult callCustomerAction(String system, String user, int seconds, int maxTokens) {
        return customerRequest(system,user,seconds,maxTokens,true,customerThinking);
    }
    private TextResult customerRequest(String system, String user, int seconds, int maxTokens, boolean nativeAction, boolean thinking) {
        AiRuntimeConfig config = customerConfig();
        if (!StringUtils.hasText(config.endpoint()) || !StringUtils.hasText(config.apiKey()))
            throw new BaseException("AI 服务尚未配置");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.min(5, seconds)));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(1, seconds - Math.min(5, seconds))));
        RestTemplate client = new RestTemplate(factory);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.apiKey());
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("model", config.model()); body.put("temperature", 0.0); body.put("max_tokens", maxTokens);
        body.put("messages", List.of(Map.of("role","system","content",system),Map.of("role","user","content",user)));
        if(usesDeepSeekThinkingSwitch(config)) {
            body.put("thinking",Map.of("type",thinking?"enabled":"disabled"));
            if(!nativeAction) body.put("response_format",Map.of("type","json_object"));
        }
        if(nativeAction) {
            body.putAll(customerActionFields(config,thinking));
        }
        String endpoint = config.endpoint().endsWith("/chat/completions") ? config.endpoint() : config.endpoint()+"/chat/completions";
        long started = System.nanoTime();
        try {
            Map<?,?> response = client.postForObject(endpoint,new HttpEntity<>(body,headers),Map.class);
            if(response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty())
                throw new BaseException("AI 返回为空");
            Map<?,?> choice=(Map<?,?>) choices.get(0);
            Map<?,?> message=(Map<?,?>) choice.get("message");
            Object rawContent=message==null?null:message.get("content");
            if(nativeAction && message!=null && message.get("tool_calls") instanceof List<?> calls && calls.size()==1) {
                Map<?,?> call=(Map<?,?>)calls.getFirst(); Map<?,?> function=(Map<?,?>)call.get("function");
                if(function!=null && "customerAction".equals(function.get("name"))) rawContent=function.get("arguments");
            }
            Map<String,Object> usage=new LinkedHashMap<>();
            if(response.get("usage") instanceof Map<?,?> raw) raw.forEach((k,v)->usage.put(String.valueOf(k),v));
            return new TextResult(rawContent instanceof String content?stripFence(content):"",
                Objects.toString(response.get("model"),config.model()),usage,elapsedMillis(started),Objects.toString(choice.get("finish_reason"),"unknown"));
        } catch(HttpStatusCodeException ex) { throw AiToolProviderException.from(ex); }
    }

    static Map<String,Object> customerActionFields(AiRuntimeConfig config,boolean thinking) {
        Map<String,Object> fields=new LinkedHashMap<>();
        fields.put("tools",List.of(Map.of("type","function","function",Map.of("name","customerAction","description","提交经过校验的客服意图与条件变更",
            "parameters",Map.of("type","object","properties",Map.of("intent",Map.of("type","string","enum",List.of("CHAT","QUESTION","COLLECT","RECOMMEND","DETAIL","COMPARE","STATE","DIAGNOSE","EXPLAIN")),"reply",Map.of("type","string"),"changes",CustomerActionSchema.changes(),"profileDefaults",CustomerActionSchema.changes(),"projectFeedback",Map.of("type","array","maxItems",3,"items",Map.of("type","object","properties",Map.of("operation",Map.of("type","string","enum",List.of("DISMISS","RESTORE","RESET")),"position",Map.of("type","integer","minimum",0,"maximum",3),"quote",Map.of("type","string","maxLength",200)),"required",List.of("operation","position","quote"),"additionalProperties",false)),"beforeTask",Map.of("type","object","properties",Map.of("intent",Map.of("type","string","enum",List.of("STATE","EXPLAIN")),"quote",Map.of("type","string","maxLength",300)),"required",List.of("intent","quote"),"additionalProperties",false),"positions",Map.of("type","array","items",Map.of("type","integer")),"preferences",Map.of("type","array","maxItems",6,"items",Map.of("type","string","maxLength",200)),"pendingConditions",Map.of("type","array","items",Map.of("type","object","properties",Map.of("field",Map.of("type","string"),"quote",Map.of("type","string"),"question",Map.of("type","string")),"required",List.of("field","quote","question")))),"required",List.of("intent","reply","changes","positions"))))));
        // DeepSeek thinking rejects named/required tool_choice. Omission selects auto.
        if(!(thinking && usesDeepSeekThinkingSwitch(config)))
            fields.put("tool_choice",Map.of("type","function","function",Map.of("name","customerAction")));
        return fields;
    }
    /** Non-secret policy identity; callers hash this before storing any cache metadata. */
    public String customerPolicyIdentity() {
        AiRuntimeConfig config=customerConfig();return config.version()+"|"+config.endpoint()+"|"+config.model();
    }
    AiRuntimeConfig customerConfig() {
        AiRuntimeConfig text = configService.resolveText(null);
        return StringUtils.hasText(customerModel)
                ? new AiRuntimeConfig(text.type(), text.version(), text.endpoint(), text.apiKey(), customerModel, text.source())
                : text;
    }

    static boolean usesDeepSeekThinkingSwitch(AiRuntimeConfig config) {
        return "api.deepseek.com".equalsIgnoreCase(java.net.URI.create(config.endpoint()).getHost())
                && (config.model().startsWith("deepseek-v4") || "deepseek-flash".equals(config.model()));
    }

    private String stripFence(String content) {
        return content.replaceFirst("^\\s*```(?:json|markdown)?\\s*", "")
                .replaceFirst("\\s*```\\s*$", "").trim();
    }

    private long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }
}
