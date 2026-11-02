package com.orangejd.server.customer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "orange.customer")
public class CustomerProperties {
    private CustomerExecutionEngine.Kind executionEngine=CustomerExecutionEngine.Kind.LEGACY;
    private boolean rawAccessEnabled;
    public CustomerExecutionEngine.Kind getExecutionEngine() {return executionEngine;}
    public void setExecutionEngine(CustomerExecutionEngine.Kind value) {executionEngine=java.util.Objects.requireNonNull(value);}
    public boolean isRawAccessEnabled() {return rawAccessEnabled;}
    public void setRawAccessEnabled(boolean value) {rawAccessEnabled=value;}
    private boolean enabled;
    private boolean nativeActions;
    private String model="deepseek-flash";
    private boolean thinking;
    public String getModel() { return model; }
    public void setModel(String value) { model=value; }
    public boolean isThinking() { return thinking; }
    public void setThinking(boolean value) { thinking=value; }
    private Set<Long> allowedAccounts = Set.of();
    private boolean allAuthenticatedUsers = true;
    private int maxConcurrent = 2;
    private int maxQueued = 2;
    public int getMaxQueued() { return maxQueued; }
    public void setMaxQueued(int v) { if(v<0 || v>4)throw new IllegalArgumentException("客服等待队列必须为0至4");maxQueued=v; }
    private int runSeconds = 90;
    private int modelSeconds = 45;
    private int inputTokens = 6000;
    private int outputTokens = 1500;
    private int actionOutputTokens = 4096;
    private int evidenceOutputTokens = 2048;
    private int verificationOutputTokens = 2048;
    private boolean evidenceThinking;
    private boolean verificationThinking;
    private boolean purposeThinking;
    private int purposeOutputTokens = 2048;
    private int purposeSeconds = 30;
    private int purposeRetryReserveSeconds = 10;
    private int purposeMaxBatches = 2;
    public int getPurposeMaxBatches() { return purposeMaxBatches; }
    public void setPurposeMaxBatches(int value) { if(value<1 || value>3)throw new IllegalArgumentException("用途核对批次必须为1至3");purposeMaxBatches=value; }
    // Separate bounded phases; they never inherit global thinking or grow output on retry.
    public int getRequirementInputTokens() { return Math.min(inputTokens,4000); }
    public int getRequirementMatchOutputTokens() { return 2048; }
    public int getRequirementVerifyOutputTokens() { return 1500; }
    public boolean isPurposeThinking() { return purposeThinking; }
    public void setPurposeThinking(boolean value) { purposeThinking=value; }
    public int getPurposeOutputTokens() { return purposeOutputTokens; }
    public void setPurposeOutputTokens(int value) { purposeOutputTokens=stageBudget(value); }
    public int getPurposeSeconds() { return purposeSeconds; }
    public void setPurposeSeconds(int value) { if(value<20 || value>30)throw new IllegalArgumentException("用途核对时限必须为20至30秒");purposeSeconds=value; }
    public int getPurposeRetryReserveSeconds() { return purposeRetryReserveSeconds; }
    public void setPurposeRetryReserveSeconds(int value) { if(value<8 || value>10)throw new IllegalArgumentException("用途核对重试预留必须为8至10秒");purposeRetryReserveSeconds=value; }
    public boolean isEvidenceThinking() { return evidenceThinking; }
    public void setEvidenceThinking(boolean value) { evidenceThinking=value; }
    public boolean isVerificationThinking() { return verificationThinking; }
    public void setVerificationThinking(boolean value) { verificationThinking=value; }
    public int getActionOutputTokens() { return actionOutputTokens; }
    public void setActionOutputTokens(int v) { actionOutputTokens=stageBudget(v); }
    public int getEvidenceOutputTokens() { return evidenceOutputTokens; }
    public void setEvidenceOutputTokens(int v) { evidenceOutputTokens=stageBudget(v); }
    public int getVerificationOutputTokens() { return verificationOutputTokens; }
    public void setVerificationOutputTokens(int v) { verificationOutputTokens=stageBudget(v); }
    private int stageBudget(int v) { if(v<1500 || v>8192) throw new IllegalArgumentException("阶段输出预算无效");return v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public boolean isNativeActions() { return nativeActions; }
    public void setNativeActions(boolean v) { nativeActions=v; }
    public Set<Long> getAllowedAccounts() { return allowedAccounts; }
    public void setAllowedAccounts(Set<Long> v) { allowedAccounts = v == null ? Set.of() : Set.copyOf(v); }
    public boolean isAllAuthenticatedUsers() { return allAuthenticatedUsers; }
    public void setAllAuthenticatedUsers(boolean v) { allAuthenticatedUsers = v; }
    public boolean canAccess(Long owner) {
        return enabled && owner != null && owner > 0 && (allAuthenticatedUsers || allowedAccounts.contains(owner));
    }
    public int getMaxConcurrent() { return maxConcurrent; }
    public void setMaxConcurrent(int v) { if(v < 1 || v > 2) throw new IllegalArgumentException("客服并发必须为1或2"); maxConcurrent=v; }
    public int getRunSeconds() { return runSeconds; }
    public void setRunSeconds(int v) { if(v < 1 || v > 90) throw new IllegalArgumentException("客服时限无效"); runSeconds=v; }
    public int getModelSeconds() { return modelSeconds; }
    public void setModelSeconds(int v) { if(v < 1 || v > 45) throw new IllegalArgumentException("模型时限无效"); modelSeconds=v; }
    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int v) { if(v < 500 || v > 6000) throw new IllegalArgumentException("输入预算无效"); inputTokens=v; }
    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int v) { if(v < 100 || v > 1500) throw new IllegalArgumentException("输出预算无效"); outputTokens=v; }
}
