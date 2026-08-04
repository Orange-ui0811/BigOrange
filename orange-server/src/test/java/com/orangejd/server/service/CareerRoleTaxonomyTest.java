package com.orangejd.server.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CareerRoleTaxonomyTest {
    private final CareerRoleTaxonomy taxonomy = new CareerRoleTaxonomy();

    @Test
    void normalizesApprovedJobDirectionsIntoStableCategories() {
        assertEquals(CareerRoleTaxonomy.AI_APPLICATION_AGENT, taxonomy.normalizeDirection("RAG / Agent 开发"));
        assertEquals(CareerRoleTaxonomy.LLM_ALGORITHM_RESEARCH, taxonomy.normalizeDirection("大模型训练与推理算法"));
        assertEquals(CareerRoleTaxonomy.AI_INFRA_QUALITY, taxonomy.normalizeDirection("AI Infra 与可观测性"));
        assertEquals(CareerRoleTaxonomy.AI_FULLSTACK_BACKEND, taxonomy.normalizeDirection("AI 全栈后端开发"));
        assertEquals(CareerRoleTaxonomy.AI_PRODUCT_OPERATIONS, taxonomy.normalizeDirection("AI 产品与社区运营"));
        assertEquals(CareerRoleTaxonomy.AI_CREATIVE_INTERACTION, taxonomy.normalizeDirection("AI 游戏交互设计"));
    }

    @Test
    void aggregatesMultipleDirectionsWithoutDroppingSamples() {
        Map<String, Integer> input = new LinkedHashMap<>();
        input.put("Agent 开发", 4);
        input.put("RAG 应用", 3);
        input.put("AI 前端", 2);

        Map<String, Integer> result = taxonomy.aggregateSamples(input);

        assertEquals(7, result.get(CareerRoleTaxonomy.AI_APPLICATION_AGENT));
        assertEquals(2, result.get(CareerRoleTaxonomy.AI_CREATIVE_INTERACTION));
    }
}
