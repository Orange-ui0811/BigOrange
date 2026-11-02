package com.orangejd.server.ragb.config;

/** Optional second-stage ranking. Empty endpoint/key reuse the configured embedding service. */
public class RagBRerankProperties {
    private boolean enabled;
    private String endpoint="";
    private String apiKey="";
    private String model="bge-reranker-v2-m3";
    private int maxCandidates=80;
    private int timeoutMillis=5000;
    private int maxConcurrentRequests=2;
    private int maxRequestCharacters=300000;
    public void validate(){
        if(model==null||model.isBlank()||maxCandidates<2||maxCandidates>200||timeoutMillis<100||timeoutMillis>30000
                ||maxConcurrentRequests<1||maxConcurrentRequests>16||maxRequestCharacters<1000||maxRequestCharacters>1000000)
            throw new IllegalStateException("Invalid B rerank configuration");
    }
    public boolean isEnabled(){return enabled;}
    public void setEnabled(boolean v){enabled=v;}
    public String getEndpoint(){return endpoint;}
    public void setEndpoint(String v){endpoint=v;}
    public String getApiKey(){return apiKey;}
    public void setApiKey(String v){apiKey=v;}
    public String getModel(){return model;}
    public void setModel(String v){model=v;}
    public int getMaxCandidates(){return maxCandidates;}
    public void setMaxCandidates(int v){maxCandidates=v;}
    public int getTimeoutMillis(){return timeoutMillis;}
    public void setTimeoutMillis(int v){timeoutMillis=v;}
    public int getMaxConcurrentRequests(){return maxConcurrentRequests;}
    public void setMaxConcurrentRequests(int v){maxConcurrentRequests=v;}
    public int getMaxRequestCharacters(){return maxRequestCharacters;}
    public void setMaxRequestCharacters(int v){maxRequestCharacters=v;}
}
