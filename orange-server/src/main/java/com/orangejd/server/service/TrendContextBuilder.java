package com.orangejd.server.service;

import org.springframework.stereotype.Component;
import static com.orangejd.server.service.TrendRetrievalSupport.*;

import com.orangejd.server.dto.BeginnerProjectView;
import com.orangejd.server.dto.CareerProjectView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

@Component
public class TrendContextBuilder {
    private static final Logger log = LoggerFactory.getLogger(TrendContextBuilder.class);
    private final VectorStore vectorStore;
    private final PersonaPromptContextService personaContextService;
    private final String redisPrefix;

    public TrendContextBuilder(ObjectProvider<VectorStore> vectorStore,
                               ObjectProvider<PersonaPromptContextService> personaContextService) {
        this(vectorStore, personaContextService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TrendContextBuilder(ObjectProvider<VectorStore> vectorStore,
                               ObjectProvider<PersonaPromptContextService> personaContextService,
                               ObjectProvider<org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreProperties> config) {
        this.vectorStore = vectorStore.getIfAvailable();
        this.personaContextService = personaContextService.getIfAvailable();
        var properties = config == null ? null : config.getIfAvailable();
        this.redisPrefix = properties == null
                ? new org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreProperties().getPrefix()
                : properties.getPrefix();
    }

    @SuppressWarnings("unchecked")
    public String graphSummary(Map<String, Object> compareData) {
        return graphSummary(compareData, null);
    }

    @SuppressWarnings("unchecked")
    public String graphSummary(Map<String, Object> compareData, TrendAnalysisInput input) {
        StringBuilder text = new StringBuilder("## 本周 GitHub 热度数据（按 topic 分类）\n\n");
        Map<String, Object> thisWeek = (Map<String, Object>) compareData.get("thisWeek");
        Map<String, Object> lastWeek = (Map<String, Object>) compareData.get("lastWeek");
        List<Map<String, Object>> current = relevantGraphNodes(
                (List<Map<String, Object>>) thisWeek.get("nodes"), input);
        if (current == null || current.isEmpty()) return "";
        List<Map<String, Object>> previous = (List<Map<String, Object>>) lastWeek.get("nodes");
        Map<String, Long> lastStars = new LinkedHashMap<>();
        if (previous != null) for (Map<String, Object> node : previous) {
            lastStars.put((String) node.get("id"), ((Number) node.get("totalStars")).longValue());
        }
        if (current != null) for (Map<String, Object> node : current) {
            Map<String, Object> metrics = (Map<String, Object>) node.get("metrics");
            String crowdedness = metrics == null ? "" : String.valueOf(metrics.getOrDefault("crowdedness", ""));
            String difficulty = metrics == null ? "" : String.valueOf(metrics.getOrDefault("difficulty", ""));
            int score = metrics == null ? 0 : ((Number) metrics.getOrDefault("opportunityScore", 0)).intValue();
            int growth = metrics == null ? 0 : ((Number) metrics.getOrDefault("growth", 0)).intValue();
            String insight = metrics == null ? "" : String.valueOf(metrics.getOrDefault("insight", ""));
            Long last = lastStars.get(node.get("id"));
            String delta = last != null && last > 0 ? String.format(" (较上周 %+d%%)", growth)
                    : last == null ? " (上周无数据)" : "";
            text.append(String.format("- **%s** [%s] | ⭐ %,d | 📦 %d repos%s | 拥挤度: %s | 难度: %s | 综合评分: %d/100\n  洞察: %s\n",
                    node.get("name"), node.get("group"), ((Number) node.get("totalStars")).longValue(),
                    ((Number) node.get("repoCount")).intValue(), delta, crowdedness, difficulty, score, insight));
        }
        return text.append("\n请基于以上数据分析趋势，回答用户问题。").toString();
    }

    private static List<Map<String, Object>> relevantGraphNodes(List<Map<String, Object>> nodes,
                                                                 TrendAnalysisInput input) {
        if (nodes == null || nodes.isEmpty() || input == null) return nodes;
        String query = ((input.topics() == null ? "" : String.join(" ", input.topics())) + " "
                + nvlStatic(input.customTopics()) + " "
                + (input.languages() == null ? "" : String.join(" ", input.languages()))).toLowerCase();
        List<String> terms = java.util.Arrays.stream(query.split("[^\\p{L}\\p{N}+#.]+"))
                .map(String::strip).filter(value -> value.length() >= 2).distinct().toList();
        return nodes.stream()
                .map(node -> Map.entry(node, graphRelevance(node, terms)))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Comparator.<Map.Entry<Map<String, Object>, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparingInt(entry -> -opportunityScore(entry.getKey())))
                .limit(5).map(Map.Entry::getKey).toList();
    }

    private static int graphRelevance(Map<String, Object> node, List<String> terms) {
        String text = (String.valueOf(node.getOrDefault("id", "")) + " "
                + node.getOrDefault("name", "") + " " + node.getOrDefault("group", "")).toLowerCase();
        int score = 0;
        for (String term : terms) if (text.contains(term) || topicAliasMatch(text, term)) score++;
        return score;
    }

    private static boolean topicAliasMatch(String text, String term) {
        if ((term.contains("agent") || term.contains("智能体")) && (text.contains("agent") || text.contains("智能体"))) return true;
        if ((term.contains("rag") || term.contains("知识库")) && (text.contains("rag") || text.contains("知识库"))) return true;
        if (term.contains("数据分析") && (text.contains("data") || text.contains("分析"))) return true;
        if (term.contains("机器学习") && (text.contains("machine") || text.contains("机器学习"))) return true;
        return false;
    }

    @SuppressWarnings("unchecked")
    private static int opportunityScore(Map<String, Object> node) {
        Object metrics = node.get("metrics");
        Object score = metrics instanceof Map<?, ?> map ? map.get("opportunityScore") : null;
        return score instanceof Number number ? number.intValue() : 0;
    }

    public String profile(TrendAnalysisInput input, Long accountId) {
        StringBuilder profile = new StringBuilder("## 用户画像\n\n");
        if (present(input.topics())) profile.append("- 感兴趣方向: ").append(String.join("、", input.topics()));
        appendCustom(profile, input.customTopics());
        profile.append("\n- 目的: ").append(nvl(input.purpose()));
        appendCustom(profile, input.customPurpose());
        profile.append("\n- 编程经验: ").append(nvl(input.experience()));
        appendCustom(profile, input.customExp());
        profile.append('\n');
        if (present(input.languages())) profile.append("- 掌握语言: ").append(String.join("、", input.languages()));
        appendCustom(profile, input.customLang());
        profile.append("\n- 时间投入: ").append(nvl(input.timeCommitment()));
        appendCustom(profile, input.customTime());
        String confirmed = Boolean.TRUE.equals(input.usePersona()) ? confirmedPersona(accountId) : "";
        if (!confirmed.isEmpty()) profile.append("\n\n").append(confirmed);
        return profile.append('\n').toString();
    }

    public String simpleRag(String question) {
        if (vectorStore == null) return "";
        try {
            List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(question).topK(2).similarityThreshold(.5).build());
            StringBuilder text = new StringBuilder();
            for (int index = 0; index < docs.size(); index++) {
                text.append("参考资料").append(index + 1).append("：").append(docs.get(index).getText()).append('\n');
            }
            return text.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    public String rag(TrendAnalysisInput input) {
        return retrieve(input).text();
    }

    public record Retrieval(String text, Map<String, Object> trace) {}

    /** Read-only lab adapter using the same retrieval and evidence compression as platform requests. */
    public Retrieval previewKnowledge(String question) {
        if (question == null || question.isBlank() || question.length() > 2000) throw new IllegalArgumentException("评测问题无效");
        return retrieve(new TrendAnalysisInput(List.of(), null, null, List.of(), null, question,
                null, null, null, null, true, false, false, false));
    }

    public Retrieval retrieve(TrendAnalysisInput input) {
        long started = System.nanoTime();
        if (!Boolean.TRUE.equals(input.useRag())) return retrieval("", "DISABLED", List.of(), List.of(), started);
        if (vectorStore == null) return retrieval("", "UNAVAILABLE", List.of(), List.of(), started);
        try {
            List<String> queries = retrievalQueries(input);
            if (queries.isEmpty()) return retrieval("", "NO_TOPIC", queries, List.of(), started);
            Map<String, WeightedDocument> seen = new LinkedHashMap<>();
            int failures = 0;
            for (int route = 0; route <= queries.size(); route++) try {
                boolean lexical = route == queries.size();
                List<Document> matches;
                if (lexical) matches = TrendLabObservation.measure("A.lexical", () -> TrendRedisMetadata.lexical(vectorStore, redisPrefix, TrendTopicGate.lexicalQuery(input)));
                else {
                    var request = SearchRequest.builder().query(queries.get(route)).topK(50).similarityThreshold(.55).build();
                    matches = TrendLabObservation.measure("A.embeddingAndVector", () -> vectorStore.similaritySearch(request));
                    var rawMatches=matches;
                    matches = TrendLabObservation.measure("A.restoreMetadata", () -> TrendRedisMetadata.restore(vectorStore, redisPrefix, rawMatches));
                }
                long filteringStarted=System.nanoTime();
                for (int rank = 0; rank < matches.size(); rank++) {
                    Document document = matches.get(rank);
                    String body = document.getText();
                    if (body == null || body.isBlank() || !TrendTopicGate.accepts(document, input)) continue;
                    String key = documentKey(document);
                    double routeWeight = lexical ? .20 : route == 0 ? .55 : .25;
                    double score = routeWeight / (20 + rank + 1);
                    seen.merge(key, new WeightedDocument(document, score),
                            (left, right) -> new WeightedDocument(left.document(), left.score() + right.score()));
                }
                TrendLabObservation.elapsed("A.topicGateAndFusion",filteringStarted);
            } catch (Exception error) {
                failures++;
                log.debug("[RAG] 单维度检索异常: {}", error.getMessage());
            }
            long selectionStarted=System.nanoTime();
            java.util.Set<String> projects = new java.util.HashSet<>();
            int[] jobFragments = {0};
            List<Document> selected = seen.values().stream()
                    .sorted(Comparator.comparingDouble((WeightedDocument value) -> value.score() * (1 + ("MANUAL".equals(value.document().getMetadata().get("source")) ? .05 : 0.0)
                            + Math.min(.2, sourceBoost(value.document(), input)))).reversed()
                            .thenComparing(value -> documentKey(value.document())))
                    .map(WeightedDocument::document)
                    .filter(document -> TrendEvidenceBinder.repositoryUrl(document) != null
                            || ("MANUAL".equals(document.getMetadata().get("source"))
                            && document.getMetadata().get("projectId") != null))
                    .filter(document -> projects.add(TrendTopicGate.projectKey(document)))
                    .filter(document -> !"JOB_POSTING".equals(document.getMetadata().get("source")) || jobFragments[0]++ == 0)
                    .limit(3).toList();
            TrendLabObservation.elapsed("A.selectProjects",selectionStarted);
            TrendEvidenceCardFormatter.Rendered rendered = TrendLabObservation.measure("A.renderEvidence", () -> TrendEvidenceCardFormatter.render(selected));
            List<Map<String, Object>> evidence = new ArrayList<>();
            for (int index = 0; index < selected.size(); index++) {
                Document document = selected.get(index);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("evidenceId", "E" + (index + 1));
                item.put("documentKey", documentKey(document));
                item.put("source", document.getMetadata().get("source"));
                item.put("title", document.getMetadata().get("docTitle"));
                item.put("sourceRef", document.getMetadata().get("sourceRef"));
                item.put("projectId", document.getMetadata().get("projectId"));
                item.put("projectType", document.getMetadata().get("projectType"));
                item.put("mappingStatus", document.getMetadata().get("mappingStatus"));
                item.put("repositoryUrl", TrendEvidenceBinder.repositoryUrl(document));
                item.put("text", document.getText());
                if (index < rendered.cards().size()) {
                    item.put("card", rendered.cards().get(index));
                    item.put("promptMode", rendered.modes().get(index));
                }
                evidence.add(item);
            }
            Retrieval retrieval = retrieval(rendered.text(),
                    failures > 0 ? "PARTIAL_FAILURE" : selected.isEmpty() ? "EMPTY" : "USED",
                    queries, evidence, started);
            retrieval.trace().put("candidateCount", seen.size());
            retrieval.trace().put("selectedCount", selected.size());
            retrieval.trace().put("confidence", selected.size() >= 2 ? "HIGH" : selected.size() == 1 ? "MEDIUM" : "NONE");
            retrieval.trace().put("ragApplied", !selected.isEmpty());
            retrieval.trace().put("evidenceFormat", "CARD_V2_ADAPTIVE");
            retrieval.trace().put("evidenceOriginalChars", rendered.originalChars());
            retrieval.trace().put("evidencePromptChars", rendered.renderedChars());
            retrieval.trace().put("evidenceBudgetChars", TrendEvidenceCardFormatter.MAX_CONTEXT_CHARS);
            retrieval.trace().put("evidenceFallbackCount", rendered.fallbackCount());
            retrieval.trace().put("evidenceCompressionRatio", rendered.originalChars() == 0 ? 0.0
                    : 1.0 - (double) rendered.renderedChars() / rendered.originalChars());
            return retrieval;
        } catch (Exception error) {
            log.warn("[RAG] 检索失败，跳过增强: {}", error.getMessage());
            return retrieval("", "FAILED", List.of(), List.of(), started);
        }
    }

    private static Retrieval retrieval(String text, String status, List<String> queries,
                                       List<Map<String, Object>> evidence, long started) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("strategyVersion", "hybrid-topic-project-v6-adaptive-card");
        trace.put("status", status);
        trace.put("queries", queries);
        trace.put("evidence", evidence);
        trace.put("durationMs", (System.nanoTime() - started) / 1_000_000.0);
        return new Retrieval(text, trace);
    }

    public String beginnerCases(List<BeginnerProjectView> cases) {
        if (cases == null || cases.isEmpty()) return "";
        StringBuilder text = new StringBuilder();
        for (BeginnerProjectView project : cases) text.append("- ID=").append(project.id())
                .append("; candidateId=B:").append(project.id())
                .append("; repository=").append(project.fullName()).append("; url=").append(project.htmlUrl())
                .append("; difficulty=").append(project.difficulty()).append("; score=").append(project.beginnerScore())
                .append("; reason=").append(project.beginnerReason()).append("; verifiedAt=")
                .append(project.lastVerifiedAt()).append('\n');
        return text.toString();
    }

    public String careerCases(List<CareerProjectView> cases) {
        if (cases == null || cases.isEmpty()) return "";
        StringBuilder text = new StringBuilder();
        for (CareerProjectView project : cases) text.append("- ID=").append(project.id())
                .append("; candidateId=C:").append(project.id())
                .append("; repository=").append(project.fullName()).append("; url=").append(project.htmlUrl())
                .append("; role=").append(project.bestRoleName()).append("; score=").append(project.overallScore())
                .append("; careerValue=").append(project.careerValue()).append("; verifiedAt=")
                .append(project.lastVerifiedAt()).append('\n');
        return text.toString();
    }

    public String confirmedPersona(Long accountId) {
        return personaContextService == null ? "" : personaContextService.confirmedContext(accountId);
    }

    private void appendCustom(StringBuilder value, String custom) {
        if (custom != null && !custom.isEmpty()) value.append('（').append(custom).append('）');
    }
    private boolean present(List<?> value) { return value != null && !value.isEmpty(); }
    private String joined(List<String> value) { return present(value) ? String.join(" ", value) : ""; }
    private String nvl(String value) { return value == null || value.isEmpty() ? "未填写" : value; }

    static List<String> retrievalQueries(TrendAnalysisInput input) { return TrendRetrievalSupport.retrievalQueries(input); }
    private record WeightedDocument(Document document, double score) {}
}
