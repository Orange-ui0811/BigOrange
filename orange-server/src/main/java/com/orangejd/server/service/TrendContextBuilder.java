package com.orangejd.server.service;

import com.orangejd.server.dto.BeginnerProjectView;
import com.orangejd.server.dto.CareerProjectView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

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

    public TrendContextBuilder(ObjectProvider<VectorStore> vectorStore,
                               ObjectProvider<PersonaPromptContextService> personaContextService) {
        this.vectorStore = vectorStore.getIfAvailable();
        this.personaContextService = personaContextService.getIfAvailable();
    }

    @SuppressWarnings("unchecked")
    public String graphSummary(Map<String, Object> compareData) {
        StringBuilder text = new StringBuilder("## 本周 GitHub 热度数据（按 topic 分类）\n\n");
        Map<String, Object> thisWeek = (Map<String, Object>) compareData.get("thisWeek");
        Map<String, Object> lastWeek = (Map<String, Object>) compareData.get("lastWeek");
        List<Map<String, Object>> current = (List<Map<String, Object>>) thisWeek.get("nodes");
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
        if (vectorStore == null || !Boolean.TRUE.equals(input.useRag())) return "";
        try {
            List<String> queries = List.of(
                    "技术方向：" + joined(input.topics()) + " 开源项目入门实践 " + nvlEmpty(input.customTopics()),
                    "技术学习目的：" + nvl(input.purpose()) + " " + nvlEmpty(input.customPurpose())
                            + " " + purposeContext(nvl(input.purpose())),
                    "编程经验：" + nvl(input.experience()) + " " + nvlEmpty(input.customExp()) + " 学习路径入门",
                    "时间投入：" + nvl(input.timeCommitment()) + " " + nvlEmpty(input.customTime()) + " 学习计划 时间管理");
            Map<String, WeightedDocument> seen = new LinkedHashMap<>();
            for (String query : queries) try {
                List<Document> matches = vectorStore.similaritySearch(SearchRequest.builder()
                        .query(query).topK(6).similarityThreshold(.55).build());
                for (int rank = 0; rank < matches.size(); rank++) {
                    Document document = matches.get(rank);
                    String body = document.getText();
                    String key = String.valueOf(document.getMetadata().getOrDefault("docId", ""))
                            + ':' + document.getMetadata().getOrDefault("chunkIndex",
                            body.substring(0, Math.min(50, body.length())));
                    double score = 1.0 / (rank + 1) + sourceBoost(document, input);
                    seen.merge(key, new WeightedDocument(document, score),
                            (left, right) -> new WeightedDocument(left.document(), left.score() + right.score()));
                }
            } catch (Exception error) {
                log.debug("[RAG] 单维度检索异常: {}", error.getMessage());
            }
            List<Document> selected = seen.values().stream()
                    .sorted(Comparator.comparingDouble(WeightedDocument::score).reversed())
                    .limit(4).map(WeightedDocument::document).toList();
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < selected.size(); index++) {
                Document document = selected.get(index);
                result.append("【片段").append(index + 1).append("】");
                Object title = document.getMetadata().get("docTitle");
                if (title != null) result.append(" 来源：").append(title);
                result.append('\n').append(document.getText()).append("\n\n");
            }
            return result.toString();
        } catch (Exception error) {
            log.warn("[RAG] 检索失败，跳过增强: {}", error.getMessage());
            return "";
        }
    }

    private double sourceBoost(Document document, TrendAnalysisInput input) {
        String source = String.valueOf(document.getMetadata().getOrDefault("source", ""));
        if ("BEGINNER_PROJECT".equals(source)) {
            if (Boolean.TRUE.equals(input.preferBeginnerProjects())) return .85;
            if (nvlEmpty(input.experience()).contains("新手") || nvlEmpty(input.purpose()).contains("学习")) return .25;
        }
        if ("CAREER_PROJECT".equals(source)) {
            if (Boolean.TRUE.equals(input.preferCareerProjects())) return .85;
            if (nvlEmpty(input.purpose()).contains("求职") || nvlEmpty(input.customPurpose()).contains("求职")) return .25;
        }
        return 0;
    }

    public String beginnerCases(List<BeginnerProjectView> cases) {
        if (cases == null || cases.isEmpty()) return "";
        StringBuilder text = new StringBuilder();
        for (BeginnerProjectView project : cases) text.append("- ID=").append(project.id())
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
    private String nvlEmpty(String value) { return value == null ? "" : value; }
    private String purposeContext(String purpose) {
        if (purpose.contains("求职")) return "面试技巧 项目经验 GitHub作品集";
        if (purpose.contains("学习")) return "学习路径 入门教程 从零开始";
        if (purpose.contains("赚钱")) return "商业变现 开源项目商业化";
        if (purpose.contains("探索")) return "技术趋势 新兴方向 兴趣探索";
        return "";
    }

    private record WeightedDocument(Document document, double score) {}
}
