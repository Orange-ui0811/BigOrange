package com.orangejd.server.service;

import com.orangejd.server.foundation.external.AiRuntimeConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrendPromptFactoryTest {
    @Test
    @SuppressWarnings("unchecked")
    void v2UsesCompactCandidateBoundedContract() {
        var runtime = new AiRuntimeConfig("project", "v1", "https://ai.gitee.com/v1",
                "key", "model", "TEST");

        Map<String, Object> body = new TrendPromptFactory().v2(runtime, "candidateId=B:1");
        List<Map<String, String>> messages = (List<Map<String, String>>) body.get("messages");
        String system = messages.getFirst().get("content");

        assertEquals(4096, body.get("max_tokens"));
        assertEquals(.25, body.get("temperature"));
        assertTrue(system.contains("candidateId"));
        assertTrue(system.contains("facts是项目功能和技术栈的唯一事实来源"));
        assertFalse(system.contains("80字以上"));
        assertEquals(system.indexOf("你是AI辅助编程项目推荐专家"),
                system.lastIndexOf("你是AI辅助编程项目推荐专家"));
    }
}
