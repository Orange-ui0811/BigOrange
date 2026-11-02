package com.orangejd.server.service;

import com.orangejd.server.foundation.external.AiRuntimeConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TrendPromptFactory {
    private static final String V1_SYSTEM = "你是开源趋势分析师。基于提供的真实 GitHub 数据和知识库参考资料，分析技术趋势。用 Markdown 格式回复。";
    private static final String V2_SYSTEM =
            "你是AI辅助编程项目推荐专家。只能从用户消息中带candidateId的候选选择真实项目，"
            + "不得编造仓库、URL或验证结论。Evidence Card的facts是项目功能和技术栈的唯一事实来源，"
            + "不得根据仓库名称、常识或模型记忆补充facts未提供的能力。输出严格JSON，不要Markdown或额外文字：\n"
            + "{\"analysis\":{\"trend\":\"趋势\",\"prospect\":\"前景\",\"demand\":\"需求\","
            + "\"summary\":\"50字内总结\"},\"recommendations\":[{"
            + "\"candidateId\":\"B:数字、C:数字或E数字\",\"name\":\"中文描述名\","
            + "\"description\":\"项目定位与核心功能，40-80字\",\"difficulty\":\"入门/中等/进阶\","
            + "\"difficultyReason\":\"结合用户基础\",\"vibeCodingReason\":\"适合AI辅助开发的原因\","
            + "\"recommendReason\":\"结合学习或求职目标，60-100字\",\"prerequisites\":\"前置知识\","
            + "\"evidenceIds\":[\"E1\"],\"score\":85}],\"learning\":{"
            + "\"steps\":[\"第一步\",\"第二步\",\"第三步\"],\"timeline\":\"预计周期\","
            + "\"tips\":\"建议\"}}\n"
            + "recommendations允许0-3个；有可靠候选时优先给2个。analysis和learning字段必须完整。";

    public Map<String, Object> v1(AiRuntimeConfig runtime, String prompt) {
        return body(runtime.model(), V1_SYSTEM, prompt, .7, 2000);
    }

    public Map<String, Object> v2(AiRuntimeConfig runtime, String prompt) {
        return body(runtime.model(), V2_SYSTEM, prompt, .25, 4096);
    }

    private Map<String, Object> body(String model, String system, String prompt,
                                     double temperature, Integer maxTokens) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model", model);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", system));
        messages.add(message("user", prompt));
        result.put("messages", messages);
        result.put("temperature", temperature);
        if (maxTokens != null) result.put("max_tokens", maxTokens);
        return result;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> value = new LinkedHashMap<>();
        value.put("role", role);
        value.put("content", content);
        return value;
    }
}
