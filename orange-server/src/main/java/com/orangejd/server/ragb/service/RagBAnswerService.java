package com.orangejd.server.ragb.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;
import com.orangejd.server.foundation.external.ExternalAiClient;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.domain.RagBExperimentRun;
import com.orangejd.server.ragb.mapper.RagBMapper;
import com.orangejd.server.ragb.retrieval.RagBSearchConstraints;
import com.orangejd.server.ragb.retrieval.RagBSearchHit;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RagBAnswerService {
    private final RagBProperties properties;
    private final RagBSearchService search;
    private final ExternalAiClient ai;
    private final AiRuntimeConfigService runtimeConfigs;
    private final RagBCitationValidator citations;
    private final RagBMapper mapper;
    private final ObjectMapper objectMapper;

    public RagBAnswerService(RagBProperties properties, RagBSearchService search, ExternalAiClient ai,
                             AiRuntimeConfigService runtimeConfigs, RagBCitationValidator citations,
                             RagBMapper mapper, ObjectMapper objectMapper) {
        this.properties = properties;
        this.search = search;
        this.ai = ai;
        this.runtimeConfigs = runtimeConfigs;
        this.citations = citations;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> preview(String question, int topK, double threshold,
                                       RagBSearchConstraints constraints) {
        if (!properties.isEnabled()) throw new IllegalStateException("B 版 RAG 实验模块未启用");
        RagBSearchService.SearchResult retrieved = search.retrieve(question, topK, threshold, constraints);
        RagBExperimentRun run = begin(question, topK, threshold, retrieved);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runKey", run.getRunKey());
        result.put("question", question);
        result.put("generation", retrieved.generation());
        result.put("adapter", retrieved.adapter());
        result.put("answerPolicy", retrieved.answerPolicy());
        result.put("evidence", citations(retrieved.hits()));
        if (!"ANSWER_WITH_CITATIONS".equals(retrieved.answerPolicy())) {
            result.put("status", retrieved.status());
            result.put("publishable", false);
            result.put("answer", refusal(retrieved.answerPolicy()));
            complete(run, "SUCCEEDED", result, null);
            return result;
        }
        long started = System.nanoTime();
        try {
            ExternalAiClient.TextResult generated = ai.callTextMeasured(null, systemPrompt(), userPrompt(question, retrieved.hits()),
                    0.1, "rag-b-answer-preview");
            String answer = generated.text();
            result.put("generationModel", generated.model());
            result.put("generationUsage", generated.usage());
            RagBCitationValidator.Validation validation = citations.validate(answer, retrieved.hits());
            result.put("durationMs", Duration.ofNanos(System.nanoTime() - started).toMillis());
            result.put("answer", answer);
            result.put("citationValidation", validation);
            result.put("publishable", validation.valid());
            result.put("status", validation.valid() ? "SUPPORTED" : "UNSUPPORTED_CITATIONS");
            complete(run, "SUCCEEDED", result, null);
            return result;
        } catch (RuntimeException ex) {
            result.put("status", "GENERATION_FAILED");
            result.put("publishable", false);
            result.put("error", message(ex));
            complete(run, "FAILED", result, message(ex));
            throw ex;
        }
    }

    private RagBExperimentRun begin(String question, int topK, double threshold,
                                    RagBSearchService.SearchResult retrieved) {
        var model = runtimeConfigs.current(AiRuntimeConfigService.TEXT);
        RagBExperimentRun run = new RagBExperimentRun();
        run.setRunKey("answer-" + UUID.randomUUID());
        run.setMode("ANSWER_PREVIEW");
        run.setDatasetVersion(retrieved.generation());
        run.setModelSnapshot(json(Map.of("type", model.type(), "version", model.version(), "model", model.model())));
        run.setRetrievalSnapshot(json(Map.of("question", question, "topK", topK, "threshold", threshold,
                "adapter", retrieved.adapter(), "answerPolicy", retrieved.answerPolicy())));
        run.setStatus("RUNNING");
        mapper.insertExperiment(run);
        return run;
    }

    private void complete(RagBExperimentRun run, String status, Object result, String error) {
        run.setStatus(status);
        run.setResultJson(json(result));
        run.setLastError(error);
        mapper.completeExperiment(run);
    }

    private String systemPrompt() {
        return "你是 OrangeJD 知识库回答器。只能使用给定证据。每个包含事实的句子末尾必须添加一个或多个引用，格式严格为 [C1]。"
                + "不得使用证据中没有出现的版本、数字、依赖、功能或结论。证据冲突时明确说明，禁止编造。";
    }

    private String userPrompt(String question, List<RagBSearchHit> hits) {
        StringBuilder prompt = new StringBuilder("问题：").append(question).append("\n\n证据：\n");
        for (int i = 0; i < hits.size(); i++) {
            RagBSearchHit hit = hits.get(i);
            prompt.append("[C").append(i + 1).append("] ").append(hit.title()).append(" / ")
                    .append(hit.sectionPath() == null ? "未命名章节" : hit.sectionPath()).append("\n")
                    .append(hit.content()).append("\n\n");
        }
        prompt.append("请直接回答问题。每个事实句都必须逐句引用。证据不足的部分明确说无法确认。");
        return prompt.toString();
    }

    private List<Map<String, Object>> citations(List<RagBSearchHit> hits) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            RagBSearchHit hit = hits.get(i);
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("citation", "C" + (i + 1));
            value.put("documentId", hit.documentId());
            value.put("versionId", hit.versionId());
            value.put("chunkId", hit.chunkId());
            value.put("title", hit.title());
            value.put("sourceType", hit.sourceType());
            value.put("sourceRef", hit.sourceRef());
            value.put("sectionPath", hit.sectionPath());
            value.put("startOffset", hit.startOffset());
            value.put("endOffset", hit.endOffset());
            values.add(value);
        }
        return List.copyOf(values);
    }

    private String refusal(String policy) {
        return "REFUSE_NO_MATCH".equals(policy) ? "没有找到满足条件的知识库资料。" : "找到了相关片段，但证据不足，暂不生成结论。";
    }
    private String message(RuntimeException ex) {
        String value = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("RAG B 实验记录序列化失败", ex); }
    }
}
