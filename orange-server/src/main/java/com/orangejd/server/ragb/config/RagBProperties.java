package com.orangejd.server.ragb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orange.rag-b")
public class RagBProperties {
    private boolean enabled = false;
    private String redisPrefix = "orange:rag:b:";
    private int maxFileBytes = 1_048_576;
    private int chunkSize = 500;
    private int chunkOverlap = 50;
    private String embeddingModel = "bge-m3";
    private String embeddingProvider = "GITEE";
    private String embeddingRevision;
    private String ollamaBaseUrl = "http://localhost:11434";
    private String ollamaModel = "bge-m3:orangejd-v1";
    private String chunkPolicy = "STRUCTURE_M3_512_768_64_V1";
    private int maxChunks = 4096;
    private int embeddingBatchSize = 16;
    private int searchCandidateLimit = 20000;
    private int routeCandidateLimit = 128;
    private int fusionCandidateLimit = 80;
    private boolean githubSyncEnabled = false;
    private int githubSyncLimit = 50;
    private final RagBRerankProperties rerank = new RagBRerankProperties();

    public void validate() {
        rerank.validate();
        if (!java.util.Set.of("GITEE", "OLLAMA").contains(embeddingProvider))
            throw new IllegalStateException("B Embedding provider must be GITEE or OLLAMA");
        if ("OLLAMA".equals(embeddingProvider) && (embeddingRevision == null || !embeddingRevision.matches("sha256:[a-f0-9]{64}")))
            throw new IllegalStateException("Local B embedding requires a pinned Ollama digest");
        if (!redisPrefix.startsWith("orange:rag:b:") || redisPrefix.contains("*")) {
            throw new IllegalStateException("B 版 Redis 前缀必须位于 orange:rag:b: 命名空间且不能包含通配符");
        }
        if (chunkSize < 100 || chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalStateException("B 版分块参数无效");
        }
        if (maxChunks < 1 || embeddingBatchSize < 1 || searchCandidateLimit < 1 || githubSyncLimit < 1) {
            throw new IllegalStateException("B 版容量参数必须大于 0");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getRedisPrefix() { return redisPrefix; }
    public void setRedisPrefix(String redisPrefix) { this.redisPrefix = redisPrefix; }
    public int getMaxFileBytes() { return maxFileBytes; }
    public void setMaxFileBytes(int maxFileBytes) { this.maxFileBytes = maxFileBytes; }
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    public int getChunkOverlap() { return chunkOverlap; }
    public String getEmbeddingModel() { return embeddingModel; }
    public String getEmbeddingProvider() { return embeddingProvider; }
    public void setEmbeddingProvider(String value) { embeddingProvider = value.toUpperCase(java.util.Locale.ROOT); }
    public String getEmbeddingRevision() { return embeddingRevision; }
    public void setEmbeddingRevision(String value) { embeddingRevision = value; }
    public String getOllamaBaseUrl() { return ollamaBaseUrl; }
    public void setOllamaBaseUrl(String value) { ollamaBaseUrl = value; }
    public String getOllamaModel() { return ollamaModel; }
    public void setOllamaModel(String value) { ollamaModel = value; }
    public void setEmbeddingModel(String value) { embeddingModel = value; }
    public String getChunkPolicy() { return chunkPolicy; }
    public void setChunkPolicy(String value) { chunkPolicy = value; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
    public int getMaxChunks() { return maxChunks; }
    public void setMaxChunks(int maxChunks) { this.maxChunks = maxChunks; }
    public int getEmbeddingBatchSize() { return embeddingBatchSize; }
    public void setEmbeddingBatchSize(int embeddingBatchSize) { this.embeddingBatchSize = embeddingBatchSize; }
    public int getSearchCandidateLimit() { return searchCandidateLimit; }
    public int getRouteCandidateLimit() { return routeCandidateLimit; }
    public void setRouteCandidateLimit(int value) { if(value<20 || value>200) throw new IllegalArgumentException("召回候选数须为20至200"); routeCandidateLimit=value; }
    public int getFusionCandidateLimit() { return fusionCandidateLimit; }
    public void setFusionCandidateLimit(int value) { if(value<20 || value>200) throw new IllegalArgumentException("融合候选数须为20至200"); fusionCandidateLimit=value; }
    public void setSearchCandidateLimit(int searchCandidateLimit) { this.searchCandidateLimit = searchCandidateLimit; }
    public boolean isGithubSyncEnabled() { return githubSyncEnabled; }
    public void setGithubSyncEnabled(boolean githubSyncEnabled) { this.githubSyncEnabled = githubSyncEnabled; }
    public int getGithubSyncLimit() { return githubSyncLimit; }
    public RagBRerankProperties getRerank() { return rerank; }
    public void setGithubSyncLimit(int githubSyncLimit) { this.githubSyncLimit = githubSyncLimit; }
}
