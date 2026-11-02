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
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.template.NoOpTemplateRenderer;

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
    private final int customerInputTokens;
    private final int customerWireInputCodePoints;

    public ExternalAiClient(AiRuntimeConfigService configService) {
        this(configService, "");
    }

    public ExternalAiClient(AiRuntimeConfigService configService, String customerModel) {
        this(configService,customerModel,false);
    }

    public ExternalAiClient(AiRuntimeConfigService configService,
                            String customerModel, boolean customerThinking) {
        this(configService,customerModel,customerThinking,6000);
    }

    public ExternalAiClient(AiRuntimeConfigService configService,
                            String customerModel, boolean customerThinking,int customerInputTokens) {
        this(configService,customerModel,customerThinking,customerInputTokens,CustomerSpringAiChatModel.DEFAULT_WIRE_INPUT_CODE_POINTS);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ExternalAiClient(AiRuntimeConfigService configService,
                            @org.springframework.beans.factory.annotation.Value("${orange.customer.model:deepseek-flash}") String customerModel,
                            @org.springframework.beans.factory.annotation.Value("${orange.customer.thinking:false}") boolean customerThinking,
                            @org.springframework.beans.factory.annotation.Value("${orange.customer.input-tokens:6000}") int customerInputTokens,
                            @org.springframework.beans.factory.annotation.Value("${orange.customer.wire-input-code-points:20000}") int customerWireInputCodePoints) {
        this.configService = configService;
        this.customerModel = customerModel == null ? "" : customerModel.trim();
        this.customerThinking=customerThinking;
        this.customerInputTokens=customerInputTokens;
        if(customerWireInputCodePoints<512 || customerWireInputCodePoints>24000)throw new IllegalArgumentException("INVALID_CUSTOMER_WIRE_BUDGET");
        this.customerWireInputCodePoints=customerWireInputCodePoints;
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
        return customerRequest(system,user,seconds,maxTokens,nativeAction,thinking,(kind,payload)->{});
    }
    public TextResult callCustomerObserved(String system,String user,int seconds,int maxTokens,boolean nativeAction,Boolean thinking,java.util.function.BiConsumer<String,Map<String,Object>> observer) {
        return customerRequest(system,user,seconds,maxTokens,nativeAction,thinking==null?customerThinking:thinking,observer);
    }
    private TextResult customerRequest(String system,String user,int seconds,int maxTokens,boolean nativeAction,boolean thinking,java.util.function.BiConsumer<String,Map<String,Object>> observer) {
        ChatResponse response=callCustomerPrompt(new Prompt(List.of(new SystemMessage(system),new UserMessage(user))),
            seconds,maxTokens,nativeAction,thinking,observer);
        Map<String,Object> usage=response.getMetadata().get(CustomerSpringAiChatModel.RAW_USAGE);
        Number duration=response.getMetadata().get(CustomerSpringAiChatModel.DURATION_MS);
        return new TextResult(response.getResult().getOutput().getText(),response.getMetadata().getModel(),usage,
            duration.longValue(),response.getResult().getMetadata().getFinishReason());
    }

    /** Typed Spring AI boundary; no memory advisor, framework retry or automatic tool execution. */
    public ChatResponse callCustomerPrompt(Prompt prompt,int seconds,int maxTokens,boolean nativeAction,boolean thinking,
                                           java.util.function.BiConsumer<String,Map<String,Object>> observer) {
        var driver=new CustomerSpringAiChatModel(customerConfig(),seconds,maxTokens,nativeAction,thinking,customerInputTokens,customerWireInputCodePoints,observer);
        // Prompts already contain JSON and untrusted history; never interpret their braces as templates.
        return ChatClient.create(driver).prompt(prompt).templateRenderer(new NoOpTemplateRenderer()).call().chatResponse();
    }

    static Map<String,Object> customerActionFields(AiRuntimeConfig config,boolean thinking) {
        Map<String,Object> fields=new LinkedHashMap<>();
        fields.put("tools",List.of(Map.of("type","function","function",Map.of("name","customerAction","description","提交经过校验的客服意图与条件变更",
            "parameters",Map.of("type","object","properties",Map.of("intent",Map.of("type","string","enum",List.of("CHAT","QUESTION","COLLECT","RECOMMEND","DETAIL","COMPARE","STATE","DIAGNOSE","EXPLAIN")),"reply",Map.of("type","string"),"changes",CustomerActionSchema.changes(),"profileDefaults",CustomerActionSchema.changes(),"projectFeedback",Map.of("type","array","maxItems",3,"items",Map.of("type","object","properties",Map.of("operation",Map.of("type","string","enum",List.of("DISMISS","RESTORE","RESET")),"position",Map.of("type","integer","minimum",0,"maximum",3),"quote",Map.of("type","string","maxLength",200)),"required",List.of("operation","position","quote"),"additionalProperties",false)),"beforeTask",Map.of("type","object","properties",Map.of("intent",Map.of("type","string","enum",List.of("STATE","EXPLAIN")),"quote",Map.of("type","string","maxLength",300)),"required",List.of("intent","quote"),"additionalProperties",false),"positions",Map.of("type","array","items",Map.of("type","integer")),"preferences",Map.of("type","array","maxItems",6,"items",Map.of("type","string","maxLength",200)),"pendingConditions",Map.of("type","array","items",Map.of("type","object","properties",Map.of("field",Map.of("type","string"),"quote",Map.of("type","string"),"question",Map.of("type","string")),"required",List.of("field","quote","question")))),"required",List.of("intent","reply","changes","positions"))))));
        // Extend the native proposal schema; business services still validate IDs, quotes and lifecycle.
        var tool=new LinkedHashMap<String,Object>((Map<String,Object>)((List<?>)fields.get("tools")).getFirst());
        var function=new LinkedHashMap<String,Object>((Map<String,Object>)tool.get("function"));
        var parameters=new LinkedHashMap<String,Object>((Map<String,Object>)function.get("parameters"));
        var properties=new LinkedHashMap<String,Object>((Map<String,Object>)parameters.get("properties"));
        for(String key:List.of("taskAction","questionAnswer","clarification"))properties.put(key,Map.of("type","object"));
        properties.put("requirementProposals",Map.of("type","array","maxItems",32,"items",Map.of("type","object")));
        properties.put("needsHistory",Map.of("type","boolean","description","当前上下文不足，需要在服务端受控范围内回查本会话原话"));
        properties.put("historyQuery",Map.of("type","string","maxLength",300,"description","用于定位原话的对象或关键词，不包含身份参数"));
        parameters.put("properties",properties);function.put("parameters",parameters);tool.put("function",function);fields.put("tools",List.of(tool));
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
