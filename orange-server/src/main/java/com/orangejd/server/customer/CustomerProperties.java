package com.orangejd.server.customer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "orange.customer")
public class CustomerProperties {
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
