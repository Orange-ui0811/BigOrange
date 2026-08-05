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
            "你是 Vibe Coding（AI 辅助编程）项目推荐专家。Vibe Coding 指利用 AI（如 Claude、Cursor、Copilot、ChatGPT 等）辅助编写代码的新型开发方式。" +
            "你的任务是推荐适合通过 Vibe Coding 方式参与或学习的开源项目。" +
            "你是 Vibe Coding（AI 辅助编程）项目推荐专家。Vibe Coding 指利用 AI（如 Claude、Cursor、Copilot、ChatGPT 等）辅助编写代码的新型开发方式。" +
            "你的任务是推荐适合通过 Vibe Coding 方式参与或学习的开源项目。" +
            "基于用户画像和 GitHub 真实数据，给出详细个人化 Vibe Coding 项目推荐。\n\n" +
            "输出严格 JSON（不要 markdown 代码块，不要额外文字）：\n" +
            "{\n  \"analysis\": {\n    \"trend\": \"当前发展趋势（增长还是稳定还是衰退，增长速度如何）\",\n" +
            "    \"prospect\": \"发展前景（未来1-2年的机会和方向）\",\n    \"demand\": \"市场需求（企业招聘、商业应用、社区活跃度）\",\n" +
            "    \"summary\": \"一句话总结（50字）\"\n  },\n  \"recommendations\": [{\n" +
            "    \"name\": \"项目名称（用中文描述性名称，绝对不要用GitHub仓库英文原名。如推荐dify要说'开源LLM应用开发平台'而非'dify'，推荐langchain要说'大语言模型应用框架'而非'langchain'）\",\n" +
            "    \"description\": \"详细介绍这个项目是什么、解决什么问题、核心功能（80字以上）\",\n" +
            "    \"difficulty\": \"入门/中等/进阶\",\n    \"difficultyReason\": \"为什么是这个难度（结合用户的技术基础分析）\",\n" +
            "    \"vibeCodingReason\": \"为什么这个项目适合 Vibe Coding（AI 辅助编程方式参与）\",\n" +
            "    \"recommendReason\": \"推荐原因（100字以上）。必须结合用户的目的（学习/求职/赚钱/兴趣）详细分析：求职目的→该领域面试官看重什么能力、这个项目能证明什么；学习目的→学习路径和成长价值；赚钱目的→商业价值和变现方式。其中针对目的的专门分析不少于50字\",\n" +
            "    \"prerequisites\": \"学习该项目需要的前置知识和技能\",\n" +
            "    \"references\": [{\"name\": \"参考项目英文名\", \"url\": \"https://github.com/xxx\"}],\n    \"score\": 85\n  }],\n" +
            "  \"learning\": {\n    \"steps\": [\"第一步做什么\", \"第二步做什么\", \"第三步做什么\"],\n" +
            "    \"timeline\": \"预计多长时间能看到成果\",\n    \"tips\": \"学习过程中的建议\"\n  }\n}\n\n" +
            "每个字段都要详细写，不要省略，不要简写。recommendations 1-3 个。analysis 四个子字段必填。" +
            "description 至少 80 字。recommendReason 至少 60 字。";

    public Map<String, Object> v1(AiRuntimeConfig runtime, String prompt) {
        return body(runtime.model(), V1_SYSTEM, prompt, .7, 2000);
    }

    public Map<String, Object> v2(AiRuntimeConfig runtime, String prompt) {
        return body(runtime.model(), V2_SYSTEM, prompt, .5, null);
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
