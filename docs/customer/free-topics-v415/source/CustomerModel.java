package com.orangejd.server.customer;

import org.springframework.stereotype.Service;
import com.orangejd.server.foundation.external.ExternalAiClient;
import org.springframework.web.client.ResourceAccessException;
import java.time.Instant;
import java.util.*;

@Service
public class CustomerModel {
    private final ExternalAiClient ai;
    private final CustomerProperties config;
    private final CustomerJson json;
    public CustomerModel(ExternalAiClient ai, CustomerProperties config, CustomerJson json) { this.ai=ai; this.config=config; this.json=json; }
    public Map<String,Object> call(String system,Object input,Instant deadline,List<Map<String,Object>> usage) {
        String user=json.write(input);
        String phase=CustomerPurpose.PLAN_PROMPT.equals(system)?"QUERY_PLAN":CustomerPurpose.REVIEW_PROMPT.equals(system)?"PURPOSE_REVIEW":CustomerState.PROMPT.equals(system)?"ACTION":CustomerEvidence.VERIFY_PROMPT.equals(system)?"VERIFY":CustomerEvidence.PROMPT.equals(system)?"EVIDENCE":CustomerRecommendationAdvice.PROMPT.equals(system)?"ADVICE":"OTHER";
        int budget=switch(phase) {case "ACTION","ADVICE","PURPOSE_REVIEW"->config.getActionOutputTokens();case "VERIFY"->config.getVerificationOutputTokens();case "EVIDENCE"->config.getEvidenceOutputTokens();default->config.getOutputTokens();};
        boolean evidencePhase=phase.equals("EVIDENCE") || phase.equals("VERIFY") || phase.equals("ADVICE") || phase.equals("QUERY_PLAN") || phase.equals("PURPOSE_REVIEW");
        boolean thinking=phase.equals("PURPOSE_REVIEW")?true:phase.equals("EVIDENCE")?config.isEvidenceThinking():phase.equals("VERIFY")?config.isVerificationThinking():(phase.equals("ADVICE") || phase.equals("QUERY_PLAN") || phase.equals("PURPOSE_REVIEW"))?false:config.isThinking();
        if(estimate(system+user)>config.getInputTokens()) throw new IllegalArgumentException("CONTEXT_BUDGET_EXCEEDED");
        for(int attempt=0;attempt<2;attempt++) {
            int seconds=(int)Math.min(config.getModelSeconds(),java.time.Duration.between(Instant.now(),deadline).toSeconds());
            if(seconds<2) throw new IllegalStateException("DEADLINE_EXCEEDED");
            long started=System.nanoTime();
            Map<String,Object> recorded=null;
            try {
                boolean nativeAction=config.isNativeActions() && CustomerState.PROMPT.equals(system) && attempt==0;
                ExternalAiClient.TextResult response=nativeAction
                    ? ai.callCustomerAction(system,user,seconds,budget)
                    : evidencePhase?ai.callCustomer(system,user,seconds,budget,thinking):ai.callCustomer(system,user,seconds,budget);
                recorded=new LinkedHashMap<>(Map.of("model",response.model(),"usage",response.usage(),"durationMs",response.durationMs(),"attempt",attempt+1,"status","SUCCEEDED","costUsd","UNKNOWN"));
                usage.add(recorded);
                recorded.put("protocol",nativeAction?"NATIVE_ACTION":"JSON_OBJECT");
                recorded.put("phase",phase); recorded.put("requestedModel",config.getModel());
                recorded.put("finishReason",response.finishReason()); recorded.put("outputBudget",budget);
                recorded.put("thinking",thinking);
                if("length".equals(response.finishReason())) throw new IllegalArgumentException("MODEL_OUTPUT_TRUNCATED");
                if(response.text()==null || response.text().isBlank()) throw new IllegalArgumentException("EMPTY_MODEL_OUTPUT");
                Map<String,Object> parsed=json.read(response.text());
                if(CustomerState.PROMPT.equals(system)) {
                    if(!CustomerState.INTENTS.contains(parsed.get("intent"))) throw new IllegalArgumentException("INVALID_ACTION_SCHEMA");
                    for(String key:List.of("changes","profileDefaults")) if(parsed.containsKey(key) && !(parsed.get(key) instanceof Map<?,?>)) throw new IllegalArgumentException("INVALID_ACTION_SCHEMA");
                    for(String key:List.of("changes","profileDefaults")) if(parsed.get(key) instanceof Map<?,?> changes && changes.get("topic") instanceof Map<?,?> topic && topic.get("value")!=null && !"CLEAR".equals(topic.get("operation"))) {
                        try { CustomerTopics.normalize(topic.get("value")); }
                        catch(IllegalArgumentException invalid) { throw new IllegalArgumentException("INVALID_ACTION_SCHEMA"); }
                    }
                    if("CHAT".equals(parsed.get("intent")) && (!(parsed.get("reply") instanceof String text) || text.isBlank())) throw new IllegalArgumentException("INVALID_ACTION_SCHEMA");
                }
                return parsed;
            } catch(RuntimeException ex) {
                if(recorded!=null) { recorded.put("status","FAILED"); recorded.put("errorType",ex.getClass().getSimpleName());recorded.put("errorCode",CustomerRecovery.code(ex)); }
                else usage.add(Map.of("attempt",attempt+1,"status","FAILED","errorType",ex.getClass().getSimpleName(),"durationMs",(System.nanoTime()-started)/1_000_000,"costUsd","UNKNOWN","phase",phase,"errorCode",CustomerRecovery.code(ex)));
                boolean malformedAction=CustomerState.PROMPT.equals(system)
                    && Set.of("INVALID_MODEL_JSON","EMPTY_MODEL_OUTPUT","INVALID_ACTION_SCHEMA").contains(Objects.toString(ex.getMessage(),""));
                boolean truncated="MODEL_OUTPUT_TRUNCATED".equals(ex.getMessage()) && budget<8192 && java.time.Duration.between(Instant.now(),deadline).toSeconds()>=10;
                if(attempt==1 || !(retryable(ex) || malformedAction || truncated)) throw ex;
                if(truncated) budget=Math.min(8192,budget*2);
            }
        }
        throw new IllegalStateException("MODEL_FAILED");
    }
    private boolean retryable(RuntimeException ex) {
        if(ex instanceof com.orangejd.server.foundation.aitool.AiToolProviderException p) return p.httpStatus()==429 || p.httpStatus()>=500;
        return ex instanceof ResourceAccessException;
    }
    /** Conservative bound for mixed CJK/code input, including metadata. */
    public static int estimate(String text) { return text.codePointCount(0,text.length()); }
}
