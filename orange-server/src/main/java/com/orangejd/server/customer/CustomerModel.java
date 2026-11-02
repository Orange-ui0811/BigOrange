package com.orangejd.server.customer;

import org.springframework.stereotype.Service;
import com.orangejd.server.foundation.external.ExternalAiClient;
import org.springframework.web.client.ResourceAccessException;
import java.time.Instant;
import java.time.Clock;
import java.util.*;

@Service
public class CustomerModel {
    private final ExternalAiClient ai;
    private final CustomerProperties config;
    private final CustomerJson json;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public CustomerModel(ExternalAiClient ai, CustomerProperties config, CustomerJson json) { this(ai,config,json,Clock.systemUTC()); }
    CustomerModel(ExternalAiClient ai, CustomerProperties config, CustomerJson json,Clock clock) { this.ai=ai; this.config=config; this.json=json; this.clock=clock; }
    public String policyIdentity() { return config.getModel()+"|"+ai.customerPolicyIdentity()+"|purpose-v3:"+config.isPurposeThinking()+":"+config.getPurposeOutputTokens(); }
    public Map<String,Object> call(String system,Object input,Instant deadline,List<Map<String,Object>> usage) {
        String user=json.write(input);
        boolean summary=CustomerSummaryService.PROMPT.equals(system);
        String requestSystem=system;
        String phase=summary?"SUMMARY":CustomerRequirementMatcher.PROMPT.equals(system)?"REQUIREMENT_MATCH":CustomerRequirementMatcher.VERIFY_PROMPT.equals(system)?"REQUIREMENT_VERIFY":CustomerPurpose.PLAN_PROMPT.equals(system)?"QUERY_PLAN":CustomerPurpose.REVIEW_PROMPT.equals(system)?"PURPOSE_REVIEW":CustomerState.PROMPT.equals(system)?"ACTION":CustomerEvidence.VERIFY_PROMPT.equals(system)?"VERIFY":CustomerEvidence.PROMPT.equals(system)?"EVIDENCE":CustomerRecommendationAdvice.PROMPT.equals(system)?"ADVICE":"OTHER";
        boolean requirement=phase.startsWith("REQUIREMENT_");
        // Retries share one stage budget; they do not each receive another full model timeout.
        boolean purpose=phase.equals("PURPOSE_REVIEW");
        int stageSeconds=switch(phase) {case "REQUIREMENT_MATCH"->20;case "REQUIREMENT_VERIFY"->12;case "QUERY_PLAN"->12;case "PURPOSE_REVIEW"->config.getPurposeSeconds();case "EVIDENCE"->30;case "VERIFY","ADVICE"->20;default->config.getModelSeconds();};
        if(summary)stageSeconds=8;
        Instant stageDeadline=clock.instant().plusSeconds(stageSeconds);
        if(!"OTHER".equals(phase) && stageDeadline.isBefore(deadline))deadline=stageDeadline;
        int budget=switch(phase) {case "REQUIREMENT_MATCH"->config.getRequirementMatchOutputTokens();case "REQUIREMENT_VERIFY"->config.getRequirementVerifyOutputTokens();case "ACTION","ADVICE"->config.getActionOutputTokens();case "PURPOSE_REVIEW"->config.getPurposeOutputTokens();case "VERIFY"->config.getVerificationOutputTokens();case "EVIDENCE"->config.getEvidenceOutputTokens();default->config.getOutputTokens();};
        boolean evidencePhase=requirement || phase.equals("EVIDENCE") || phase.equals("VERIFY") || phase.equals("ADVICE") || phase.equals("QUERY_PLAN") || phase.equals("PURPOSE_REVIEW");
        boolean thinking=requirement?false:purpose?config.isPurposeThinking():phase.equals("EVIDENCE")?config.isEvidenceThinking():phase.equals("VERIFY")?config.isVerificationThinking():(phase.equals("ADVICE") || phase.equals("QUERY_PLAN"))?false:config.isThinking();
        if(summary){budget=1000;thinking=false;evidencePhase=true;}
        var contextBudget=CustomerContextBudget.check(phase,system,input,summary?Math.min(4000,config.getInputTokens()):requirement?config.getRequirementInputTokens():config.getInputTokens(),0,json);
        if(usage instanceof CustomerUsageLedger ledger)ledger.payload("CONTEXT_MANIFEST",contextBudget.manifest());
        contextBudget.requireFits();
        for(int attempt=0;attempt<(summary?1:2);attempt++) {
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("RUN_STOPPED");
            if(usage instanceof CustomerUsageLedger ledger)ledger.check();
            long remaining=java.time.Duration.between(clock.instant(),deadline).toSeconds();
            int seconds=(int)Math.min(config.getModelSeconds(),remaining);
            // Reserve a useful retry only when the first attempt still has at least 10 seconds.
            if(purpose && attempt==0 && remaining>=10+config.getPurposeRetryReserveSeconds())
                seconds=Math.min(seconds,(int)remaining-config.getPurposeRetryReserveSeconds());
            if(seconds<2) throw new IllegalStateException("DEADLINE_EXCEEDED");
            long started=System.nanoTime();
            Map<String,Object> recorded=null;
            try {
                boolean nativeAction=config.isNativeActions() && CustomerState.PROMPT.equals(system) && attempt==0;
                ExternalAiClient.TextResult response=usage instanceof CustomerUsageLedger ledger
                    ? ai.callCustomerObserved(requestSystem,user,seconds,budget,nativeAction,evidencePhase?thinking:null,(kind,payload)->ledger.payload(kind,Map.of("phase",phase,"payload",payload)))
                    : nativeAction
                    ? ai.callCustomerAction(requestSystem,user,seconds,budget)
                    : evidencePhase?ai.callCustomer(requestSystem,user,seconds,budget,thinking):ai.callCustomer(requestSystem,user,seconds,budget);
                recorded=new LinkedHashMap<>(Map.of("model",response.model(),"usage",response.usage(),"durationMs",response.durationMs(),"attempt",attempt+1,"status","SUCCEEDED","costUsd","UNKNOWN"));
                recorded.put("protocol",nativeAction?"NATIVE_ACTION":"JSON_OBJECT");
                recorded.put("phase",phase); recorded.put("requestedModel",config.getModel());
                recorded.put("finishReason",response.finishReason()); recorded.put("outputBudget",budget);
                recorded.put("thinking",thinking);
                recorded.put("timeoutSeconds",seconds);
                if(usage instanceof CustomerUsageLedger ledger)ledger.check();
                if(usage instanceof CustomerUsageLedger ledger)ledger.payload("MODEL_ATTEMPT",Map.of("phase",phase,"attempt",attempt+1,"system",requestSystem,"input",input,"response",Objects.toString(response.text(),""),"protocol",nativeAction?"NATIVE_ACTION":"JSON_OBJECT","outputBudget",budget,"thinking",thinking));
                if("length".equals(response.finishReason())) throw new IllegalArgumentException("MODEL_OUTPUT_TRUNCATED");
                if(response.text()==null || response.text().isBlank()) throw new IllegalArgumentException("EMPTY_MODEL_OUTPUT");
                Map<String,Object> parsed=json.read(response.text());
                if(requirement && !(parsed.get(phase.equals("REQUIREMENT_MATCH")?"matches":"supportedIds") instanceof List<?>))throw new IllegalArgumentException("INVALID_REQUIREMENT_SCHEMA");
                if(purpose) {
                    if(!(input instanceof Map<?,?>))throw new CustomerPurpose.InvalidOutput(List.of("INPUT_CANDIDATES_REQUIRED"));
                    var errors=CustomerPurpose.outputErrors(parsed,(Map<String,Object>)input);
                    if(!errors.isEmpty()) {
                        var partial=attempt==1?CustomerPurpose.partialOutput(parsed,(Map<String,Object>)input):null;
                        if(partial==null)throw new CustomerPurpose.InvalidOutput(errors);
                        parsed=partial;recorded.put("outputValidation","PARTIAL_VALIDATED");recorded.put("purposeOutputErrors",errors);
                    } else parsed=Map.of("matches",parsed.get("matches")); // Internal partial markers can only be added by the server.
                }
                if(CustomerState.PROMPT.equals(system)) {
                    if(!CustomerState.INTENTS.contains(parsed.get("intent"))) throw new IllegalArgumentException("INVALID_ACTION_SCHEMA");
                    for(String key:List.of("changes","profileDefaults")) if(parsed.containsKey(key) && !(parsed.get(key) instanceof Map<?,?>)) throw new IllegalArgumentException("INVALID_ACTION_SCHEMA");
                    // Field-level errors belong to reconcile: preserve valid changes and the conversation.
                    if("CHAT".equals(parsed.get("intent")) && (!(parsed.get("reply") instanceof String text) || text.isBlank())) throw new IllegalArgumentException("INVALID_ACTION_SCHEMA");
                }
                usage.add(Collections.unmodifiableMap(new LinkedHashMap<>(recorded)));
                return parsed;
            } catch(RuntimeException ex) {
                boolean responseAvailable=recorded!=null;
                if(recorded!=null) { recorded.put("status","FAILED"); recorded.put("errorType",ex.getClass().getSimpleName());recorded.put("errorCode",CustomerRecovery.code(ex)); }
                else recorded=new LinkedHashMap<>(Map.of("attempt",attempt+1,"status","FAILED","errorType",ex.getClass().getSimpleName(),"durationMs",(System.nanoTime()-started)/1_000_000,"costUsd","UNKNOWN","phase",phase,"errorCode",CustomerRecovery.code(ex)));
                recorded.putAll(CustomerRecovery.details(ex));
                recorded.put("requestedModel",config.getModel());recorded.put("outputBudget",budget);
                recorded.put("thinking",thinking);recorded.put("timeoutSeconds",seconds);
                long retryRemaining=java.time.Duration.between(clock.instant(),deadline).toSeconds();
                boolean malformedAction=CustomerState.PROMPT.equals(system)
                    && Set.of("INVALID_MODEL_JSON","EMPTY_MODEL_OUTPUT","INVALID_ACTION_SCHEMA").contains(Objects.toString(ex.getMessage(),""));
                boolean malformedPurpose=purpose && Set.of("INVALID_PURPOSE_REVIEW","INVALID_MODEL_JSON","EMPTY_MODEL_OUTPUT").contains(Objects.toString(ex.getMessage(),""));
                boolean malformedRequirement=requirement && Set.of("INVALID_REQUIREMENT_SCHEMA","INVALID_MODEL_JSON","EMPTY_MODEL_OUTPUT").contains(Objects.toString(ex.getMessage(),""));
                String repairedSystem=requestSystem;
                if(malformedPurpose) {
                    List<String> errors=ex instanceof CustomerPurpose.InvalidOutput invalid?invalid.errors:List.of("RETURN_COMPLETE_JSON_OBJECT_WITH_MATCHES_ARRAY");
                    recorded.put("purposeOutputErrors",errors);
                    repairedSystem=CustomerPurpose.correctionPrompt(errors);
                }
                boolean truncated="MODEL_OUTPUT_TRUNCATED".equals(ex.getMessage()) && (budget<8192 || (purpose && thinking));
                boolean eligible=retryable(ex) || malformedAction || malformedPurpose || malformedRequirement || truncated;
                boolean stopped=Thread.currentThread().isInterrupted() || "RUN_STOPPED".equals(ex.getMessage());
                boolean contextFits=!malformedPurpose || estimate(repairedSystem+user)<=config.getInputTokens();
                boolean willRetry=!summary && attempt==0 && eligible && !stopped && contextFits && retryRemaining>=(truncated?10:purpose?8:2);
                recorded.put("retryDecision",willRetry?"RETRY":stopped?"STOPPED":attempt==1?"ATTEMPTS_EXHAUSTED":!contextFits?"CONTEXT_BUDGET_EXCEEDED":eligible?"INSUFFICIENT_TIME":"NOT_RETRYABLE");
                recorded.put("remainingSeconds",Math.max(0,retryRemaining));
                usage.add(Collections.unmodifiableMap(new LinkedHashMap<>(recorded)));
                if(usage instanceof CustomerUsageLedger ledger)ledger.payload("MODEL_FAILURE",Map.of("phase",phase,"attempt",attempt+1,"system",requestSystem,"input",input,"errorCode",CustomerRecovery.code(ex),"responseAvailable",responseAvailable,"retryDecision",recorded.get("retryDecision")));
                if(!willRetry) throw ex;
                if(malformedPurpose) requestSystem=repairedSystem;
                // Same input/schema: never retry moderation rejection or split its evidence to bypass review.
                if(truncated) {
                    if(purpose && thinking) thinking=false;
                    else if(!requirement)budget=Math.min(8192,budget*2);
                }
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
