package com.orangejd.server.ai;

import com.orangejd.server.config.AiEmbeddingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Gitee AI Embedding 模型适配器
 * <p>
 * 继承 Spring AI 的 {@link AbstractEmbeddingModel}，完整实现 {@link org.springframework.ai.embedding.EmbeddingModel} 接口。
 * 封装 Gitee AI 的 /v1/embeddings 端点（OpenAI 兼容格式），不依赖 DashScope/Ollama 官方 starter。
 * <p>
 * 模型：{@code bge-small-zh-v1.5}，输出 512 维向量。
 * <p>
 * 借鉴 tjxt-javaai02：DashScope 的 EmbeddingModel 由 starter 自动配置，本项目改为手动实现。
 * 这样后续若要做 ChatModel+Advisor 全链路，无需改接口签名。
 */
public class GiteeAiEmbeddingModel extends AbstractEmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(GiteeAiEmbeddingModel.class);
    static final int DEFAULT_DIMENSION = 512;

    private final RestTemplate restTemplate;
    private final AiEmbeddingProperties properties;

    public GiteeAiEmbeddingModel(RestTemplate restTemplate, AiEmbeddingProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * Spring AI EmbeddingModel 主入口——Advisor/VectorStore 通过此方法调用。
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = request.getInstructions();
        List<float[]> vectors = embed(inputs);

        List<Embedding> result = new ArrayList<>(vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            result.add(new Embedding(vectors.get(i), i));
        }
        return new EmbeddingResponse(result);
    }

    /**
     * 批量 embedding——HTTP 直调 Gitee AI /v1/embeddings
     */
    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        String url = properties.getEndpoint().replaceAll("/$", "") + "/embeddings";

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("input", texts);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + properties.getApiKey());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            com.orangejd.server.foundation.external.AiAcceptanceBudget.beforeRequest("GITEE_EMBEDDING");
            @SuppressWarnings("unchecked")
            ResponseEntity<Map> response = com.orangejd.server.service.TrendLabObservation.measure("embedding.http."+properties.getModel(),
                    () -> restTemplate.exchange(url, HttpMethod.POST, entity, Map.class));
            if (response.getBody() == null || !response.getBody().containsKey("data")) {
                log.error("Embedding API 返回异常: {}", response.getBody());
                throw new RuntimeException("Embedding API 返回格式异常");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.getBody().get("data");
            List<float[]> embeddings = new ArrayList<>(dataList.size());
            for (Map<String, Object> item : dataList) {
                @SuppressWarnings("unchecked")
                List<Double> raw = (List<Double>) item.get("embedding");
                float[] vec = new float[raw.size()];
                for (int i = 0; i < raw.size(); i++) {
                    vec[i] = raw.get(i).floatValue();
                }
                embeddings.add(vec);
            }
            return embeddings;
        } catch (Exception e) {
            log.error("Embedding API 调用失败: {}", e.getMessage());
            throw new RuntimeException("Embedding 服务调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public float[] embed(String text) {
        List<float[]> result = embed(Collections.singletonList(text));
        return result.isEmpty() ? new float[0] : result.get(0);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public int dimensions() {
        return "bge-m3".equals(properties.getModel()) ? 1024 : DEFAULT_DIMENSION;
    }
}
