package com.orangejd.server.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class CareerRoleTaxonomy {
    public static final String VERSION = "career-taxonomy-v1";

    public static final String AI_APPLICATION_AGENT = "AI_APPLICATION_AGENT";
    public static final String LLM_ALGORITHM_RESEARCH = "LLM_ALGORITHM_RESEARCH";
    public static final String AI_INFRA_QUALITY = "AI_INFRA_QUALITY";
    public static final String AI_FULLSTACK_BACKEND = "AI_FULLSTACK_BACKEND";
    public static final String AI_PRODUCT_OPERATIONS = "AI_PRODUCT_OPERATIONS";
    public static final String AI_CREATIVE_INTERACTION = "AI_CREATIVE_INTERACTION";

    private static final List<RoleCategory> CATEGORIES = List.of(
            new RoleCategory(AI_APPLICATION_AGENT, "AI 应用与 Agent", "Agent、RAG、LLM 应用与 AI 应用开发"),
            new RoleCategory(LLM_ALGORITHM_RESEARCH, "大模型算法与研究", "模型算法、训练推理、后训练与研究"),
            new RoleCategory(AI_INFRA_QUALITY, "AI 基础设施与质量", "AI Infra、架构、性能、可观测性、评测与测试"),
            new RoleCategory(AI_FULLSTACK_BACKEND, "AI 全栈与后端工程", "AI 原生应用、全栈、后端与效能工具"),
            new RoleCategory(AI_PRODUCT_OPERATIONS, "AI 产品与运营", "AI 产品设计、产品经理、运营与开发者社区"),
            new RoleCategory(AI_CREATIVE_INTERACTION, "AI 创意与交互", "AI 前端、创意设计、交互体验与游戏 PCG"));

    private static final Map<String, List<String>> KEYWORDS = Map.of(
            AI_APPLICATION_AGENT, List.of("agent", "rag", "llm app", "chatbot", "assistant", "workflow", "tool calling", "mcp"),
            LLM_ALGORITHM_RESEARCH, List.of("training", "inference", "model", "transformer", "fine-tun", "pytorch", "evaluation", "benchmark"),
            AI_INFRA_QUALITY, List.of("infra", "observability", "monitor", "performance", "testing", "quality", "platform", "gateway", "sdk"),
            AI_FULLSTACK_BACKEND, List.of("fullstack", "backend", "spring", "java", "api", "database", "server", "typescript", "python"),
            AI_PRODUCT_OPERATIONS, List.of("product", "analytics", "dashboard", "community", "feedback", "growth", "workflow", "template"),
            AI_CREATIVE_INTERACTION, List.of("frontend", "react", "vue", "next", "design", "creative", "visual", "game", "pcg", "ui"));

    public List<RoleCategory> categories() {
        return CATEGORIES;
    }

    public List<String> keywords(String roleCode) {
        return KEYWORDS.getOrDefault(roleCode, List.of());
    }

    public String normalizeDirection(String direction) {
        String value = direction == null ? "" : direction.trim().toLowerCase(Locale.ROOT);
        if (containsAny(value, "agent", "llm 应用", "ai 应用", "rag")) return AI_APPLICATION_AGENT;
        if (containsAny(value, "llm 算法", "llm 研究", "算法", "训练", "推理", "研究")) return LLM_ALGORITHM_RESEARCH;
        if (containsAny(value, "infra", "架构", "效能", "测试", "质量", "可观测")) return AI_INFRA_QUALITY;
        if (containsAny(value, "全栈", "后端", "后台", "开发")) return AI_FULLSTACK_BACKEND;
        if (containsAny(value, "产品", "运营", "社区")) return AI_PRODUCT_OPERATIONS;
        if (containsAny(value, "游戏", "创意", "前端", "交互", "pcg")) return AI_CREATIVE_INTERACTION;
        return AI_APPLICATION_AGENT;
    }

    public Map<String, Integer> aggregateSamples(Map<String, Integer> directionCounts) {
        Map<String, Integer> result = new LinkedHashMap<>();
        CATEGORIES.forEach(category -> result.put(category.code(), 0));
        directionCounts.forEach((direction, count) ->
                result.merge(normalizeDirection(direction), count == null ? 0 : count, Integer::sum));
        return result;
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    public record RoleCategory(String code, String name, String description) {}
}
