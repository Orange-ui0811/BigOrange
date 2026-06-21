package com.orangejd.server.controller.app;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 项目方案生成器 — 五阶段 AI 规划
 */
@RestController
@RequestMapping("/app/plan")
public class AppPlanController {

    @Value("${orange.ai-trend.endpoint:}")
    private String apiEndpoint;

    @Value("${orange.ai-trend.api-key:}")
    private String apiKey;

    @Value("${orange.ai-trend.model:DeepSeek-V4-Pro}")
    private String model;

    @Autowired
    private com.orangejd.server.mapper.PlanHistoryMapper planHistoryMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String[] PHASE_TITLES = {
        "", "需求理解", "成功标准定义", "约束风险识别", "可执行任务计划"
    };

    @PostMapping("/generate")
    public Result<?> generate(@RequestBody Map<String, Object> body) {
        int phase = ((Number) body.getOrDefault("phase", 1)).intValue();
        String mode = (String) body.getOrDefault("mode", "full");
        String summary = (String) body.getOrDefault("summary", "");
        String extra = (String) body.getOrDefault("extra", "");
        String prevContents = (String) body.getOrDefault("prevContents", "");

        if (summary.isEmpty()) return Result.error("请输入项目描述");

        // 精简模式：只影响最终计划（阶段4），前三个阶段和详细模式一样
        boolean lite = "lite".equals(mode) && phase == 4;
        String prompt = lite ? buildPromptLite(phase, summary, extra, prevContents)
                             : buildPrompt(phase, summary, extra, prevContents);
        if (prompt == null) return Result.error("无效的阶段编号");

        try {
            String url = apiEndpoint.trim();
            if (!url.startsWith("http")) url = "https://" + url;
            if (!url.endsWith("/chat/completions")) url = url.replaceAll("/$", "") + "/chat/completions";

            Map<String, Object> reqBody = new LinkedHashMap<>();
            reqBody.put("model", model);

            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> sys = new LinkedHashMap<>();
            sys.put("role", "system");
            sys.put("content", "你是一个专业的技术架构师和项目规划专家。你的任务是帮助用户将模糊的项目想法转化为清晰、可执行的计划。用 Markdown 格式回复，结构清晰，分析深入。");
            messages.add(sys);

            Map<String, String> user = new LinkedHashMap<>();
            user.put("role", "user");
            user.put("content", prompt);
            messages.add(user);

            reqBody.put("messages", messages);
            reqBody.put("temperature", 0.5);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(reqBody, headers);

            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            if (resp.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("content", msg.getOrDefault("content", ""));
                    result.put("phase", phase);
                    result.put("phaseTitle", PHASE_TITLES[phase]);
                    return Result.success(result);
                }
            }
            return Result.error("AI 返回为空");
        } catch (Exception e) {
            return Result.error("AI 调用失败: " + (e.getMessage() != null ? e.getMessage().substring(0, Math.min(80, e.getMessage().length())) : ""));
        }
    }

    private String buildPrompt(int phase, String summary, String extra, String prevContents) {
        switch (phase) {
            case 1:
                return "请进入「需求理解」阶段，暂时不要实现任何代码。\n\n" +
                    "基于以下项目信息，请完成需求理解分析：\n\n" +
                    "【项目概要】\n" + summary + "\n\n" +
                    "【附加信息】\n" + (extra.isEmpty() ? "（无额外信息）" : extra) + "\n\n" +
                    "请输出以下内容：\n\n" +
                    "1. **准确复述需求**：区分[用户目标][技术任务][交付结果]三个层级\n" +
                    "2. **核心模块识别**：识别出需要哪些核心模块，用文字描述模块关系\n" +
                    "3. **标出不确定点**：哪些描述存在歧义？列出需要澄清的问题（5个以内）\n" +
                    "4. **MVP 边界建议**：建议最小可用版本边界——做什么，不做什么\n" +
                    "5. **最合理的技术理解**：给出你的判断并说明原因\n\n" +
                    "格式要求：使用 Markdown，结构清晰。";

            case 2:
                return "请进入「成功标准定义」阶段。\n\n" +
                    "【项目概要】\n" + summary + "\n\n" +
                    "【需求理解（阶段1已确认）】\n" + prevContents + "\n\n" +
                    "请定义可验证的成功标准：\n\n" +
                    "1. **总体 MVP 成功标准**：5-8 条可验证标准，每条标注验证方式\n" +
                    "2. **分阶段成功标准**：每个主要阶段至少 3 条具体标准\n" +
                    "3. **必须避免的回归问题**：3-5 条\n" +
                    "4. **完成证据清单**：证明项目完成的证据\n\n" +
                    "格式要求：使用 Markdown，每条标准具体可测量。";

            case 3:
                return "请进入「约束、风险与非目标」识别阶段。\n\n" +
                    "【项目概要】\n" + summary + "\n\n" +
                    "【前阶段已确认内容】\n" + prevContents + "\n\n" +
                    "请输出：\n\n" +
                    "1. **技术约束**：语言/框架/平台/设备/版本等，用表格\n" +
                    "2. **业务约束**：发布时间/权限/兼容/性能等\n" +
                    "3. **风险矩阵**：5-8 个最大技术风险，标注影响/概率/规避策略，用表格\n" +
                    "4. **非目标清单**：明确不做的事，防范围蔓延\n" +
                    "5. **冲突升级策略**：需求与约束冲突时的处理方式\n\n" +
                    "格式要求：使用 Markdown 表格。";

            case 4:
                return "请进入「创建可执行任务计划」阶段。\n\n" +
                    "【项目概要】\n" + summary + "\n\n" +
                    "【前阶段已确认的全部内容】\n" + prevContents + "\n\n" +
                    "请基于以上全部信息，创建可执行任务计划：\n\n" +
                    "1. **当前基线**：已有资产和缺口（用表格）\n" +
                    "2. **任务拆解总览**：分阶段展示每个阶段大约多久\n" +
                    "3. **详细步骤**：每个阶段拆成具体可落地步骤，每步标注目标/输入/输出\n" +
                    "4. **文件清单**：需要新增和修改的文件\n" +
                    "5. **回滚策略**\n" +
                    "6. **最终验收清单**：是否完成完成的检查项\n\n" +
                    "输出可直接用作项目开发指导文档。格式要求：使用 Markdown。";

            default:
                return null;
        }
    }

    private String buildPromptLite(int phase, String summary, String extra, String prevContents) {
        // 只有阶段4走精简
        return "请基于前三个阶段的分析，创建一份精简可执行任务计划。\n\n" +
            "【项目概要】\n" + summary + "\n\n" +
            "【前三个阶段分析】\n" + prevContents + "\n\n" +
            "请用最精炼的语言输出：\n" +
            "1. 当前基线（2-3行）\n2. 任务拆解（4-6步）\n3. 预计时间和里程碑\n4. 关键风险提示\n" +
            "格式：Markdown。控制在 500 字以内。";
    }

    // ---- 历史记录 ----

    @GetMapping("/history")
    public Result<?> history() {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId == 0) return Result.error("请先登录");
        return Result.success(planHistoryMapper.selectByAccountId(accountId, 20));
    }

    @PostMapping("/history/save")
    public Result<?> saveHistory(@RequestBody Map<String, Object> body) {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId == 0) return Result.error("请先登录");
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("accountId", accountId);
        record.put("summary", body.getOrDefault("summary", ""));
        record.put("phase1", body.getOrDefault("phase1", ""));
        record.put("phase2", body.getOrDefault("phase2", ""));
        record.put("phase3", body.getOrDefault("phase3", ""));
        record.put("phase4", body.getOrDefault("phase4", ""));
        planHistoryMapper.insert(record);
        return Result.success(Collections.singletonMap("id", record.get("id")));
    }

    @DeleteMapping("/history/{id}")
    public Result<?> deleteHistory(@PathVariable Long id) {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId == 0) return Result.error("请先登录");
        planHistoryMapper.deleteById(id, accountId);
        return Result.success();
    }

    @DeleteMapping("/history/clear")
    public Result<?> clearHistory() {
        Long accountId = BaseContext.getCurrentId();
        if (accountId == null || accountId == 0) return Result.error("请先登录");
        planHistoryMapper.deleteByAccountId(accountId);
        return Result.success();
    }
}
