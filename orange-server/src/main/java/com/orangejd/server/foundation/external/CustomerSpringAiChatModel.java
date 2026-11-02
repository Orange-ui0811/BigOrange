package com.orangejd.server.foundation.external;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;
import com.orangejd.server.foundation.aitool.AiToolProviderException;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.*;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.*;

import java.time.Duration;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Request-scoped Spring AI driver for the existing customer endpoint.
 * Vendor-specific thinking/action serialization stays here. This driver performs exactly one
 * bounded HTTP attempt: retries, tool execution and workflow state remain caller-controlled.
 */
public final class CustomerSpringAiChatModel implements ChatModel {
    public static final String RAW_USAGE="customer.providerUsage";
    public static final String DURATION_MS="customer.durationMs";
    public static final int DEFAULT_WIRE_INPUT_CODE_POINTS=20000;
    private static final ObjectMapper JSON=new ObjectMapper();
    private final AiRuntimeConfig config;
    private final int seconds,maxTokens,inputLimit,wireInputLimit;
    private final boolean nativeAction,thinking;
    private final BiConsumer<String,Map<String,Object>> observer;

    public CustomerSpringAiChatModel(AiRuntimeConfig config,int seconds,int maxTokens,boolean nativeAction,
                                    boolean thinking,int inputLimit,BiConsumer<String,Map<String,Object>> observer) {
        this(config,seconds,maxTokens,nativeAction,thinking,inputLimit,DEFAULT_WIRE_INPUT_CODE_POINTS,observer);
    }
    public CustomerSpringAiChatModel(AiRuntimeConfig config,int seconds,int maxTokens,boolean nativeAction,
                                    boolean thinking,int inputLimit,int wireInputLimit,BiConsumer<String,Map<String,Object>> observer) {
        this.config=Objects.requireNonNull(config);this.observer=Objects.requireNonNull(observer);
        if(seconds<1 || maxTokens<1 || inputLimit<1)throw new IllegalArgumentException("INVALID_CUSTOMER_MODEL_OPTIONS");
        if(wireInputLimit<512 || wireInputLimit>24000)throw new IllegalArgumentException("INVALID_CUSTOMER_WIRE_BUDGET");
        this.seconds=seconds;this.maxTokens=maxTokens;this.nativeAction=nativeAction;this.thinking=thinking;this.inputLimit=inputLimit;this.wireInputLimit=wireInputLimit;
    }

    @Override public ChatOptions getDefaultOptions() {
        return ChatOptions.builder().model(config.model()).temperature(0.0).maxTokens(maxTokens).build();
    }

    @Override public ChatResponse call(Prompt prompt) {
        if(!StringUtils.hasText(config.endpoint()) || !StringUtils.hasText(config.apiKey()))throw new BaseException("AI 服务尚未配置");
        if(Thread.currentThread().isInterrupted())throw new IllegalStateException("RUN_STOPPED");
        if(logicalCost(prompt)>inputLimit)throw new IllegalArgumentException("CONTEXT_BUDGET_EXCEEDED");
        Map<String,Object> body=requestBody(prompt);
        // Count the complete serialized request, including native tool/schema overhead, before quota or HTTP.
        // This is a conservative code-point estimate, not a provider tokenizer or fabricated usage.
        if(estimate(body)>wireInputLimit)throw new IllegalArgumentException("CUSTOMER_WIRE_BUDGET_EXCEEDED");
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.min(5,seconds)));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(1,seconds-Math.min(5,seconds))));
        var client=new RestTemplate(factory);
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.setBearerAuth(config.apiKey());
        String endpoint=config.endpoint().endsWith("/chat/completions")?config.endpoint():config.endpoint()+"/chat/completions";
        long started=System.nanoTime();
        try {
            observer.accept("MODEL_REQUEST",body);
            AiAcceptanceBudget.beforeRequest("CUSTOMER_CHAT");
            Map<?,?> response=client.postForObject(endpoint,new HttpEntity<>(body,headers),Map.class);
            if(response==null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()
                || !(choices.getFirst() instanceof Map<?,?> choice) || !(choice.get("message") instanceof Map<?,?> message))throw new BaseException("AI 返回为空");
            Map<String,Object> visible=new LinkedHashMap<>();
            visible.put("model",response.get("model"));visible.put("usage",response.get("usage"));visible.put("finish_reason",choice.get("finish_reason"));
            for(String key:List.of("content","tool_calls"))if(message.containsKey(key))visible.put(key,message.get(key));
            observer.accept("MODEL_RESPONSE",visible);
            var usage=new LinkedHashMap<String,Object>();
            if(response.get("usage") instanceof Map<?,?> raw)raw.forEach((key,value)->usage.put(key.toString(),value));
            List<AssistantMessage.ToolCall> calls=new ArrayList<>();
            if(message.get("tool_calls") instanceof List<?> rawCalls)for(Object raw:rawCalls) {
                if(raw instanceof Map<?,?> call && call.get("function") instanceof Map<?,?> function
                    && function.get("name") instanceof String name && function.get("arguments") instanceof String arguments)
                    calls.add(new AssistantMessage.ToolCall(Objects.toString(call.get("id"),""),Objects.toString(call.get("type"),"function"),name,arguments));
            }
            String text=message.get("content") instanceof String content?stripFence(content):"";
            // customerAction is a proposal schema, not an executable business tool.
            if(nativeAction && calls.size()==1 && "customerAction".equals(calls.getFirst().name()))text=stripFence(calls.getFirst().arguments());
            var metadata=ChatResponseMetadata.builder().model(Objects.toString(response.get("model"),config.model()))
                .usage(new ProviderUsage(usage)).keyValue(RAW_USAGE,Collections.unmodifiableMap(usage))
                .keyValue(DURATION_MS,Duration.ofNanos(System.nanoTime()-started).toMillis()).build();
            var generation=new Generation(new AssistantMessage(text,Map.of(),calls),ChatGenerationMetadata.builder()
                .finishReason(Objects.toString(choice.get("finish_reason"),"unknown")).build());
            return new ChatResponse(List.of(generation),metadata);
        } catch(HttpStatusCodeException ex) {throw AiToolProviderException.from(ex);}
    }

    /** Package-visible for exact protocol tests; includes all model-visible fields. */
    Map<String,Object> requestBody(Prompt prompt) {
        Objects.requireNonNull(prompt,"prompt");
        var body=new LinkedHashMap<String,Object>();var options=prompt.getOptions();
        if(options!=null && options.getModel()!=null && !config.model().equals(options.getModel()))throw new IllegalArgumentException("CUSTOMER_MODEL_OVERRIDE_NOT_ALLOWED");
        body.put("model",config.model());body.put("temperature",options!=null&&options.getTemperature()!=null?options.getTemperature():0.0);
        int requested=options!=null&&options.getMaxTokens()!=null?options.getMaxTokens():maxTokens;
        if(requested<1)throw new IllegalArgumentException("INVALID_CUSTOMER_MODEL_OPTIONS");
        body.put("max_tokens",Math.min(maxTokens,requested));
        body.put("messages",messages(prompt.getInstructions()));
        if(ExternalAiClient.usesDeepSeekThinkingSwitch(config)) {
            body.put("thinking",Map.of("type",thinking?"enabled":"disabled"));
            if(!nativeAction)body.put("response_format",Map.of("type","json_object"));
        }
        if(nativeAction)body.putAll(ExternalAiClient.customerActionFields(config,thinking));
        return body;
    }

    private static List<Map<String,Object>> messages(List<Message> instructions) {
        List<Map<String,Object>> result=new ArrayList<>();Map<String,String> pending=new LinkedHashMap<>();
        for(Message message:instructions) {
            if(message instanceof ToolResponseMessage tools) {
                if(tools.getResponses().isEmpty())throw new IllegalArgumentException("INVALID_TOOL_MESSAGE_PAIR");
                for(var response:tools.getResponses()) {
                    if(!Objects.equals(pending.remove(response.id()),response.name()))throw new IllegalArgumentException("INVALID_TOOL_MESSAGE_PAIR");
                    result.add(Map.of("role","tool","tool_call_id",response.id(),"content",Objects.toString(response.responseData(),"")));
                }
                continue;
            }
            if(!pending.isEmpty())throw new IllegalArgumentException("INVALID_TOOL_MESSAGE_PAIR");
            if(message instanceof UserMessage user && !user.getMedia().isEmpty())throw new IllegalArgumentException("CUSTOMER_MULTIMODAL_UNSUPPORTED");
            var view=new LinkedHashMap<String,Object>();view.put("role",message.getMessageType().getValue());view.put("content",Objects.toString(message.getText(),""));
            if(message instanceof AssistantMessage assistant) {
                if(!assistant.getMedia().isEmpty())throw new IllegalArgumentException("CUSTOMER_MULTIMODAL_UNSUPPORTED");
                if(assistant.hasToolCalls()) {
                    List<Map<String,Object>> calls=new ArrayList<>();
                    for(var call:assistant.getToolCalls()) {
                        if(call.id()==null || call.id().isBlank() || pending.putIfAbsent(call.id(),call.name())!=null)throw new IllegalArgumentException("INVALID_TOOL_MESSAGE_PAIR");
                        calls.add(Map.of("id",call.id(),"type","function","function",Map.of("name",call.name(),"arguments",call.arguments())));
                    }
                    view.put("tool_calls",calls);
                }
            }
            result.add(view);
        }
        if(!pending.isEmpty())throw new IllegalArgumentException("INVALID_TOOL_MESSAGE_PAIR");
        return result;
    }

    static int estimate(Map<String,Object> body) {
        try {String serialized=JSON.writeValueAsString(body);return serialized.codePointCount(0,serialized.length());}
        catch(JsonProcessingException ex) {throw new IllegalArgumentException("INVALID_CUSTOMER_MODEL_PAYLOAD",ex);}
    }
    static int logicalCost(Prompt prompt) {
        int used=0;
        for(Message message:prompt.getInstructions()) {
            String text=Objects.toString(message.getText(),"");used+=text.codePointCount(0,text.length());
            if(message instanceof AssistantMessage assistant)for(var call:assistant.getToolCalls()) {
                String arguments=Objects.toString(call.arguments(),"");used+=arguments.codePointCount(0,arguments.length());
            }
            if(message instanceof ToolResponseMessage tools)for(var response:tools.getResponses()) {
                String data=Objects.toString(response.responseData(),"");used+=data.codePointCount(0,data.length());
            }
        }
        return used;
    }
    private static String stripFence(String text) {return text.replaceFirst("^\\s*```(?:json|markdown)?\\s*","").replaceFirst("\\s*```\\s*$","").trim();}
    /** Null denotes unknown; Spring AI DefaultUsage turns missing values into zero. */
    private record ProviderUsage(Map<String,Object> raw) implements Usage {
        private Integer count(String key) {return raw.get(key) instanceof Number n?n.intValue():null;}
        @Override public Integer getPromptTokens() {return count("prompt_tokens");}
        @Override public Integer getCompletionTokens() {return count("completion_tokens");}
        @Override public Integer getTotalTokens() {return count("total_tokens");}
        @Override public Object getNativeUsage() {return raw;}
    }
}
