package com.orangejd.server.service;

import org.springframework.util.StringUtils;

import java.util.Map;

final class OpenSourcePromptFactory {

    private OpenSourcePromptFactory() {
    }

    static String issueMatch(String profile, String context) {
        return "用户条件：" + (StringUtils.hasText(profile) ? profile : "未补充")
                + "\n候选 Issue：" + context
                + "\n选择3到5条并推荐最容易完成的一条。严格输出JSON："
                + "{\"recommendedIssueId\":1,\"matches\":[{\"issueId\":1,\"reason\":\"\","
                + "\"risk\":\"\",\"difficulty\":\"入门/简单/中等\"}]}。不要输出候选外的ID。";
    }

    static String contributionGuide(Map<String, Object> issue) {
        return "仓库：" + issue.get("repositoryFullName")
                + "\nIssue：" + issue.get("titleOriginal")
                + "\n正文：" + abbreviate(String.valueOf(issue.getOrDefault("bodyOriginal", "")), 8_000)
                + "\n技术栈：" + issue.get("techStack")
                + "\nNode：" + issue.get("nodeVersion") + "，包管理器：" + issue.get("packageManager")
                + "\n贡献指南：" + issue.get("contributionUrl")
                + "\n请输出1200到1800字中文Markdown，严格包含："
                + "一、Issue要解决什么；二、开始前准备；三、推荐完成流程；四、验证清单；"
                + "五、提交PR注意事项；六、结项准备清单。"
                + "不要生成修复代码，不要虚构文件和测试结果；无法确认的内容明确标为需要用户核对。";
    }

    private static String abbreviate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
