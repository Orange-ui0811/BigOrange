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

public final class TrendRetrievalSupport {
    public static List<String> retrievalQueries(TrendAnalysisInput input) {
        String topic = ((input.topics() == null ? "" : String.join(" ", input.topics()))
                + " " + (input.customTopics() == null ? "" : input.customTopics())).strip();
        if (topic.isBlank()) return List.of();
        String language = input.languages() == null ? "" : String.join(" ", input.languages());
        String base = topic + " " + language
                + " " + (input.customLang() == null ? "" : input.customLang());
        return List.of(base.strip(), (base + " " + nvlStatic(input.purpose())
                + " " + (input.customPurpose() == null ? "" : input.customPurpose())
                + " " + nvlStatic(input.experience())).strip());
    }

    public static String documentKey(Document document) {
        Object id = document.getMetadata().get("docId");
        Object chunk = document.getMetadata().get("chunkIndex");
        return id != null && chunk != null ? id + ":" + chunk : document.getId();
    }

    public static double sourceBoost(Document document, TrendAnalysisInput input) {
        String source = String.valueOf(document.getMetadata().getOrDefault("source", ""));
        if ("BEGINNER_PROJECT".equals(source)) {
            if (Boolean.TRUE.equals(input.preferBeginnerProjects())) return .20;
            if (nvlEmpty(input.experience()).contains("新手") || nvlEmpty(input.purpose()).contains("学习")) return .05;
        }
        if ("CAREER_PROJECT".equals(source)) {
            if (Boolean.TRUE.equals(input.preferCareerProjects())) return .20;
            if (nvlEmpty(input.purpose()).contains("求职") || nvlEmpty(input.customPurpose()).contains("求职")) return .05;
        }
        return 0;
    }

    public static String purposeContext(String purpose) {
        if (purpose.contains("求职")) return "面试技巧 项目经验 GitHub作品集";
        if (purpose.contains("学习")) return "学习路径 入门教程 从零开始";
        if (purpose.contains("赚钱")) return "商业变现 开源项目商业化";
        if (purpose.contains("探索")) return "技术趋势 新兴方向 兴趣探索";
        return "";
    }

    public static String nvlStatic(String value) { return value == null ? "" : value; }

    public static String nvlEmpty(String value) { return value == null ? "" : value; }
}
